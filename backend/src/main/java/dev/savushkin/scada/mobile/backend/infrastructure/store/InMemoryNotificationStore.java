package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Потокобезопасное in-memory хранилище производственных уведомлений.
 * <p>
 * Реализует {@link NotificationRepository} — порт слоя application.
 * Данные хранятся в {@link ConcurrentHashMap} (ключ — {@code notificationId}).
 * <p>
 * <b>Архитектурная роль:</b> бывшая временная реализация порта persistence для
 * dev/prototyping. С миграции V13 заменена на перманентную
 * {@code ProductionNotificationJpaAdapter} (PostgreSQL, {@code @Primary}) и больше
 * не регистрируется как Spring-бин. Класс сохранён для использования в unit-тестах.
 *
 * <h3>Семантика хранения</h3>
 * Как и JPA-реализация, хранит полную историю: новая активация
 * ({@code notificationId == null}) получает новый синтетический id и добавляется
 * отдельной записью; переходы статусов обновляют существующую запись по id.
 *
 * <h3>Потокобезопасность</h3>
 * {@link ConcurrentHashMap} гарантирует видимость между потоками.
 */
public class InMemoryNotificationStore implements NotificationRepository {

    /** notificationId → уведомление (активное или завершённое — история). */
    private final ConcurrentHashMap<Long, ProductionNotification> store =
            new ConcurrentHashMap<>();

    /** Счётчик синтетических идентификаторов для новых активаций. */
    private final AtomicLong idSequence = new AtomicLong(0);

    @Override
    public @NonNull Optional<ProductionNotification> findByNotificationId(long notificationId) {
        return Optional.ofNullable(store.get(notificationId));
    }

    @Override
    public @NonNull List<ProductionNotification> findAllByCreatorId(@NonNull String creatorId) {
        return store.values().stream()
                .filter(n -> creatorId.equals(n.creatorId()))
                .sorted(Comparator.comparing(ProductionNotification::activatedAt).reversed())
                .toList();
    }

    @Override
    public @NonNull List<ProductionNotification> findAllAcceptedBy(@NonNull String userId) {
        return store.values().stream()
                .filter(n -> userId.equals(n.acceptedBy()))
                .sorted(Comparator.comparing(ProductionNotification::acceptedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    @Override
    public @NonNull Optional<ProductionNotification> findActiveByUnitId(@NonNull String unitId) {
        return store.values().stream()
                .filter(n -> unitId.equals(n.unitId()) && n.active())
                .findFirst();
    }

    @Override
    public @NonNull List<ProductionNotification> findAllActive() {
        return store.values().stream()
                .filter(ProductionNotification::active)
                .toList();
    }

    @Override
    public @NonNull ProductionNotification save(@NonNull ProductionNotification notification) {
        if (notification.notificationId() == null) {
            long id = idSequence.incrementAndGet();
            ProductionNotification withId = new ProductionNotification(
                    id, notification.unitId(), notification.creatorId(), notification.creatorType(),
                    notification.status(), notification.active(), notification.activatedAt(),
                    notification.deactivatedAt(), notification.acceptedBy(), notification.acceptedAt(),
                    notification.completedAt(), notification.cancelledAt(), notification.version());
            store.put(id, withId);
            return withId;
        }
        store.put(notification.notificationId(), notification);
        return notification;
    }

    @Override
    public void deactivateByUnitId(@NonNull String unitId) {
        findActiveByUnitId(unitId).ifPresent(active ->
                store.put(active.notificationId(), active.deactivate()));
    }
}
