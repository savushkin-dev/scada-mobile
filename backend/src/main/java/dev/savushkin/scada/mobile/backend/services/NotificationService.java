package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Сервис-оркестратор производственных уведомлений.
 * <p>
 * Управляет жизненным циклом уведомлений (toggle activate/deactivate)
 * и публикует {@link NotificationStateChangedEvent} для WS-рассылки.
 *
 * <h3>Поток данных</h3>
 * <ol>
 *   <li>REST-контроллер получает {@code POST /line/{unitId}/last-batch} с {@code X-User-Id}.</li>
 *   <li>Вызывается {@link #toggleNotification} — доменная логика toggle.</li>
 *   <li>При изменении состояния публикуется {@link NotificationStateChangedEvent}.</li>
 *   <li>Event listener ({@code StatusBroadcaster}) обновляет WS-projection store и рассылает.</li>
 * </ol>
 *
 * <h3>Инварианты</h3>
 * <ul>
 *   <li>Нельзя отправить уведомление от аппарата, к которому работник не закреплён → {@link NotificationAccessDeniedException}.</li>
 *   <li>Деактивировать уведомление может только создатель → {@link NotificationAlreadyActiveByOtherException}.</li>
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
                ProductionNotification deactivated = existing.deactivate();
                notificationRepository.save(deactivated);
                eventPublisher.publishEvent(
                        new NotificationStateChangedEvent(unitId, deactivated,
                                NotificationStateChangedEvent.EventType.DEACTIVATED));
                log.info("Notification deactivated: unitId='{}' by userId='{}'", unitId, userIdValue);
                return new ToggleResult.Deactivated(unitId);
            } else {
                // Другой создатель → нельзя деактивировать
                log.warn("Notification already active by other: unitId='{}', creator='{}', requester='{}'",
                        unitId, existing.creatorId(), userIdValue);
                return new ToggleResult.AlreadyActiveByOther(unitId, existing.creatorId());
            }
        }

        // 3. Активация
        ProductionNotification activated = ProductionNotification.activate(unitId, userIdValue);
        notificationRepository.save(activated);
        eventPublisher.publishEvent(
                new NotificationStateChangedEvent(unitId, activated,
                        NotificationStateChangedEvent.EventType.ACTIVATED));
        log.info("Notification activated: unitId='{}' by userId='{}'", unitId, userIdValue);
        return new ToggleResult.Activated(unitId, userIdValue);
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

    // ─── Toggle result sealed hierarchy ────────────────────────────────

    /**
     * Результат toggle-операции. sealed permits для exhaustive pattern matching.
     */
    public sealed interface ToggleResult {

        record Activated(String unitId, String creatorId) implements ToggleResult {}
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
}
