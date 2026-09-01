package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationCreatorType;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис-оркестратор производственных уведомлений.
 * <p>
 * Управляет жизненным циклом уведомлений (toggle activate/deactivate)
 * и публикует {@link NotificationStateChangedEvent} для WS-рассылки.
 *
 * <h3>Поток данных</h3>
 * <ol>
 *   <li>REST-контроллер получает {@code POST /line/{unitId}/last-batch} с пользовательским
 *       или machine-JWT (СКАДА).</li>
 *   <li>Вызывается {@link #toggleNotification} (работник) или
 *       {@link #toggleMachineNotification} (автомат) — доменная логика toggle.</li>
 *   <li>При изменении состояния публикуется {@link NotificationStateChangedEvent}.</li>
 *   <li>Event listener ({@code StatusBroadcaster}) обновляет WS-projection store и рассылает.</li>
 * </ol>
 *
 * <h3>Инварианты</h3>
 * <ul>
 *   <li>Работник не может отправить уведомление от аппарата, к которому не закреплён → {@link NotificationAccessDeniedException}.</li>
 *   <li>Автомат (СКАДА) может управлять только собственным аппаратом (проверяется контроллером по sub токена).</li>
 *   <li>Деактивировать уведомление может только создатель → {@link ToggleResult.AlreadyActiveByOther} (HTTP 409).</li>
 *   <li>На один аппарат не более одного активного уведомления (toggle semantically).</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserAssignmentRepository userAssignmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserAssignmentRepository userAssignmentRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.userAssignmentRepository = userAssignmentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Toggle-операция: активирует или деактивирует уведомление для аппарата.
     * <p>
     * Результат:
     * <ul>
     *   <li>{@link ToggleResult.Activated} — уведомление создано (новое).</li>
     *   <li>{@link ToggleResult.Deactivated} — активное уведомление снято создателем.</li>
     *   <li>{@link ToggleResult.AlreadyActiveByOther} — уведомление уже активно другим работником;
     *       деактивировать его нельзя, в HTTP — 409 Conflict.</li>
     * </ul>
     *
     * @param unitId Идентификатор аппарата.
     * @param userId Идентификатор работника.
     * @return Результат toggle-операции.
     * @throws NotificationAccessDeniedException если работник не имеет права отправлять
     *         уведомления от данного аппарата.
     */
    public ToggleResult toggleNotification(@NonNull String unitId, long userId) {
        String userIdValue = Long.toString(userId);
        // 1. Проверка прав
        if (!userAssignmentRepository.canSendNotification(userId, unitId)) {
            log.warn("Notification access denied: userId='{}' has no access to unitId='{}'",
                    userIdValue, unitId);
            throw new NotificationAccessDeniedException(userIdValue, unitId);
        }

        // 2. Проверка текущего состояния
        ProductionNotification existing = notificationRepository.findActiveByUnitId(unitId)
                .orElse(null);

        if (existing != null) {
            if (existing.creatorId().equals(userIdValue)) {
                // Тот же создатель → deactivate
                return deactivate(unitId, existing, userIdValue);
            } else {
                // Другой создатель → нельзя деактивировать
                log.warn("Notification already active by other: unitId='{}', creator='{}', requester='{}'",
                        unitId, existing.creatorId(), userIdValue);
                return new ToggleResult.AlreadyActiveByOther(unitId, existing.creatorId());
            }
        }

        // 3. Активация
        return activate(unitId, ProductionNotification.activate(unitId, userIdValue), userIdValue);
    }

    /**
     * Toggle-операция от автомата (СКАДА) с machine-JWT.
     * <p>
     * Отличия от {@link #toggleNotification(String, long)}:
     * <ul>
     *   <li>Проверка прав по {@code user_unit_assignments} не выполняется — автомат
     *       действует от собственного имени; принадлежность токена аппарату валидируется
     *       контроллером (sub токена обязан совпадать с аппаратом из пути запроса).</li>
     *   <li>Создатель — автомат ({@link NotificationCreatorType#MACHINE}),
     *       {@code creatorId} = PrintSrv instance id автомата.</li>
     * </ul>
     * Инвариант «деактивировать может только создатель» сохраняется: снять уведомление,
     * установленное работником (или наоборот), нельзя — результат {@link ToggleResult.AlreadyActiveByOther}.
     *
     * @param unitId    Идентификатор аппарата (PrintSrv instance id).
     * @param machineId PrintSrv instance id автомата из machine-JWT (sub).
     * @return Результат toggle-операции.
     */
    public ToggleResult toggleMachineNotification(@NonNull String unitId, @NonNull String machineId) {
        ProductionNotification existing = notificationRepository.findActiveByUnitId(unitId)
                .orElse(null);

        if (existing != null) {
            if (existing.creatorType() == NotificationCreatorType.MACHINE
                    && existing.creatorId().equals(machineId)) {
                return deactivate(unitId, existing, machineId);
            }
            log.warn("Notification already active by other: unitId='{}', creator='{}', requester=machine '{}'",
                    unitId, existing.creatorId(), machineId);
            return new ToggleResult.AlreadyActiveByOther(unitId, existing.creatorId());
        }

        return activate(unitId, ProductionNotification.activateAsMachine(unitId, machineId), machineId);
    }

    /**
     * Возвращает текущее активное состояние «последняя партия» по аппарату.
     * Используется REST GET-эндпоинтом — единым источником истины для фронтенда и СКАДА.
     *
     * @param unitId Идентификатор аппарата (PrintSrv instance id).
     * @return Активное уведомление или {@code Optional.empty()}, если флаг не установлен.
     */
    public @NonNull Optional<ProductionNotification> getActiveNotification(@NonNull String unitId) {
        return notificationRepository.findActiveByUnitId(unitId);
    }

    private ToggleResult activate(String unitId, ProductionNotification notification, String actorId) {
        ProductionNotification persisted = notificationRepository.save(notification);
        if (persisted == null) {
            persisted = notification;
        }
        eventPublisher.publishEvent(
            new NotificationStateChangedEvent(unitId, persisted,
                        NotificationStateChangedEvent.EventType.ACTIVATED));
        log.info("Notification activated: unitId='{}' by '{}'", unitId, actorId);
        return new ToggleResult.Activated(unitId, persisted.creatorId(), persisted.notificationId());
    }

    private ToggleResult deactivate(String unitId, ProductionNotification existing, String actorId) {
        ProductionNotification deactivated = existing.deactivate();
        notificationRepository.save(deactivated);
        eventPublisher.publishEvent(
                new NotificationStateChangedEvent(unitId, deactivated,
                        NotificationStateChangedEvent.EventType.DEACTIVATED));
        log.info("Notification deactivated: unitId='{}' by '{}'", unitId, actorId);
        return new ToggleResult.Deactivated(unitId);
    }

    /**
     * Возвращает все активные уведомления.
     * Используется для построения {@code NOTIFICATION_SNAPSHOT} при WS-коннекте.
     */
    public List<ProductionNotification> getActiveNotifications() {
        return notificationRepository.findAllActive();
    }

    /**
     * Возвращает множество аппаратов, на которые подписан конкретный работник.
     * Используется для фильтрации snapshot.
     */
    public Set<String> getSubscribedUnitIds(long userId) {
        return userAssignmentRepository.getSubscribedUnitIds(userId);
    }

    public ProductionNotification acceptNotification(long notificationId, long userId) {
        ProductionNotification notification = findNotification(notificationId);
        if (!userAssignmentRepository.getSubscribedUnitIds(userId).contains(notification.unitId())) {
            throw new NotificationAccessDeniedException(Long.toString(userId), notification.unitId());
        }
        return transition(notification, notification.accept(Long.toString(userId)),
                NotificationStateChangedEvent.EventType.ACCEPTED);
    }

    public ProductionNotification completeNotification(long notificationId, long userId) {
        ProductionNotification notification = findNotification(notificationId);
        String actorId = Long.toString(userId);
        if (!actorId.equals(notification.creatorId()) && !actorId.equals(notification.acceptedBy())) {
            throw new NotificationAccessDeniedException(actorId, notification.unitId());
        }
        return transition(notification, notification.complete(actorId),
                NotificationStateChangedEvent.EventType.COMPLETED);
    }

    public ProductionNotification cancelNotification(long notificationId, long userId) {
        ProductionNotification notification = findNotification(notificationId);
        String actorId = Long.toString(userId);
        if (!actorId.equals(notification.creatorId())) {
            throw new NotificationAccessDeniedException(actorId, notification.unitId());
        }
        return transition(notification, notification.cancel(actorId),
                NotificationStateChangedEvent.EventType.CANCELLED);
    }

    public List<ProductionNotification> getSentHistory(long userId) {
        return notificationRepository.findAllByCreatorId(Long.toString(userId));
    }

    public List<ProductionNotification> getExecutorHistory(long userId) {
        return notificationRepository.findAllAcceptedBy(Long.toString(userId));
    }

    public List<ProductionNotification> getIncoming(long userId) {
        Set<String> subscribedUnits = userAssignmentRepository.getSubscribedUnitIds(userId);
        return notificationRepository.findAllActive().stream()
                .filter(notification -> notification.status() == NotificationStatus.PENDING)
                .filter(notification -> subscribedUnits.contains(notification.unitId()))
                .toList();
    }

    private ProductionNotification findNotification(long notificationId) {
        return notificationRepository.findByNotificationId(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }

    private ProductionNotification transition(ProductionNotification current,
                                               ProductionNotification next,
                                               NotificationStateChangedEvent.EventType type) {
        notificationRepository.save(next);
        eventPublisher.publishEvent(new NotificationStateChangedEvent(next.unitId(), next, type));
        return next;
    }

    // ─── Toggle result sealed hierarchy ────────────────────────────────

    /**
     * Результат toggle-операции. sealed permits для exhaustive pattern matching.
     */
    public sealed interface ToggleResult {

        record Activated(String unitId, String creatorId, Long notificationId) implements ToggleResult {}
        record Deactivated(String unitId) implements ToggleResult {}
        record AlreadyActiveByOther(String unitId, String existingCreatorId) implements ToggleResult {}
    }

    // ─── Exceptions ────────────────────────────────────────────────────

    /**
     * Работник не имеет права отправлять уведомления от данного аппарата.
     * HTTP-маппинг: 403 Forbidden.
     */
    public static class NotificationAccessDeniedException extends RuntimeException {
        public NotificationAccessDeniedException(String userId, String unitId) {
            super("Пользователь '%s' не имеет доступа к аппарату '%s'".formatted(userId, unitId));
        }
    }

    /**
     * Уведомление уже активно другим работником (попытка деактивации чужого).
     * HTTP-маппинг: 409 Conflict. Семантически: AlreadyActiveByOther.
     */
    public static class NotificationAlreadyActiveByOtherException extends RuntimeException {
        public NotificationAlreadyActiveByOtherException(String unitId, String existingCreatorId) {
            super("Уведомление на аппарате '%s' уже активно пользователем '%s'"
                    .formatted(unitId, existingCreatorId));
        }
    }

    public static class NotificationNotFoundException extends RuntimeException {
        public NotificationNotFoundException(long notificationId) {
            super("Уведомление '%s' не найдено".formatted(notificationId));
        }
    }
}
