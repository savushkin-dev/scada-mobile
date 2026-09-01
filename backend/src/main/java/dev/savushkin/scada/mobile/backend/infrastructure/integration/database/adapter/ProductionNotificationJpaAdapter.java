package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.ProductionNotificationEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.ProductionNotificationJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * JPA-реализация {@link NotificationRepository} — перманентное хранение состояния
 * «последняя партия» в PostgreSQL (таблица {@code production_notifications}).
 * <p>
 * Заменяет in-memory реализацию ({@code InMemoryNotificationStore}): состояние
 * переживает рестарт backend и является единым источником истины для фронтенда
 * и СКАДА-систем.
 * <p>
 * Порт оперирует PrintSrv instance id (строка, напр. {@code "hassia1"}), а таблица
 * ключевана по числовому {@code unit_id} — маппинг выполняется через
 * {@link UnitJpaRepository}.
 */
@Component
@Primary
public class ProductionNotificationJpaAdapter implements NotificationRepository {

    private final ProductionNotificationJpaRepository notificationRepository;
    private final UnitJpaRepository unitRepository;

    public ProductionNotificationJpaAdapter(ProductionNotificationJpaRepository notificationRepository,
                                            UnitJpaRepository unitRepository) {
        this.notificationRepository = notificationRepository;
        this.unitRepository = unitRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<ProductionNotification> findAllByCreatorId(@NonNull String creatorId) {
        return notificationRepository.findAllByCreatorIdOrderByActivatedAtDesc(creatorId).stream()
                .map(this::toDomain).flatMap(Optional::stream).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<ProductionNotification> findAllAcceptedBy(@NonNull String userId) {
        return notificationRepository.findAllByAcceptedByOrderByAcceptedAtDesc(userId).stream()
                .map(this::toDomain).flatMap(Optional::stream).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull Optional<ProductionNotification> findByNotificationId(long notificationId) {
        return notificationRepository.findById(notificationId).flatMap(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull Optional<ProductionNotification> findActiveByUnitId(@NonNull String unitId) {
        return unitRepository.findUnitIdByPrintsrvInstanceId(unitId)
                .flatMap(notificationRepository::findByUnitIdAndActiveTrue)
                .flatMap(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public @NonNull List<ProductionNotification> findAllActive() {
        return notificationRepository.findAllByActiveTrue().stream()
                .map(this::toDomain)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Сохраняет состояние уведомления.
     * <p>
     * Новая активация ({@code notificationId == null}) вставляет новую строку —
     * так сохраняется история для sent-history / executor-history. Переходы статусов
     * (accept/complete/cancel/deactivate) несут {@code notificationId} и обновляют
     * существующую строку на месте.
     * <p>
     * Инвариант «не более одного активного уведомления на аппарат» обеспечивается
     * частичным уникальным индексом {@code ux_production_notifications_active_unit}.
     * Если аппарат с данным PrintSrv instance id не найден в БД — операция игнорируется
     * (контроллер заранее валидирует аппарат через {@code UnitMappingService}).
     */
    @Override
    @Transactional
    public @NonNull ProductionNotification save(@NonNull ProductionNotification notification) {
        ProductionNotification[] saved = new ProductionNotification[1];
        unitRepository.findUnitIdByPrintsrvInstanceId(notification.unitId()).ifPresent(unitId -> {
            ProductionNotificationEntity entity = notification.notificationId() != null
                    ? notificationRepository.findById(notification.notificationId())
                        .orElseGet(ProductionNotificationEntity::new)
                    : new ProductionNotificationEntity();
            entity.setUnitId(unitId);
            entity.setCreatorType(notification.creatorType());
            entity.setCreatorId(notification.creatorId());
            entity.setActive(notification.active());
            entity.setActivatedAt(toLocalDateTime(notification.activatedAt()));
            entity.setDeactivatedAt(toLocalDateTime(notification.deactivatedAt()));
            entity.setStatus(notification.status());
            entity.setAcceptedBy(notification.acceptedBy());
            entity.setAcceptedAt(toLocalDateTime(notification.acceptedAt()));
            entity.setCompletedAt(toLocalDateTime(notification.completedAt()));
            entity.setCancelledAt(toLocalDateTime(notification.cancelledAt()));
            ProductionNotificationEntity persisted = notificationRepository.save(entity);
            saved[0] = toDomain(persisted).orElse(notification);
        });
        return saved[0] != null ? saved[0] : notification;
    }

    @Override
    @Transactional
    public void deactivateByUnitId(@NonNull String unitId) {
        unitRepository.findUnitIdByPrintsrvInstanceId(unitId)
                .flatMap(notificationRepository::findByUnitIdAndActiveTrue)
                .ifPresent(entity -> {
                    entity.setActive(false);
                    entity.setDeactivatedAt(LocalDateTime.now(ZoneOffset.UTC));
                    notificationRepository.save(entity);
                });
    }

    private @NonNull Optional<ProductionNotification> toDomain(@NonNull ProductionNotificationEntity entity) {
        return unitRepository.findPrintsrvInstanceIdById(entity.getUnitId())
                .map(printsrvInstanceId -> new ProductionNotification(
                    entity.getId(),
                        printsrvInstanceId,
                        entity.getCreatorId(),
                        entity.getCreatorType(),
                        entity.getStatus() != null ? entity.getStatus() :
                            (entity.isActive() ? NotificationStatus.PENDING : NotificationStatus.COMPLETED),
                        entity.isActive(),
                        toInstant(entity.getActivatedAt()),
                        toInstant(entity.getDeactivatedAt()),
                        entity.getAcceptedBy(),
                        toInstant(entity.getAcceptedAt()),
                        toInstant(entity.getCompletedAt()),
                        toInstant(entity.getCancelledAt()),
                        entity.getVersion()
                ));
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
