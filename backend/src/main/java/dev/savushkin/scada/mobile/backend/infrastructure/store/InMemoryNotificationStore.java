package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище производственных уведомлений.
 * <p>
 * Реализует {@link NotificationRepository} — порт слоя application.
 * Данные хранятся в {@link ConcurrentHashMap} (ключ — {@code unitId}).
 * <p>
 * <b>Архитектурная роль:</b> временная реализация порта persistence для dev/prototyping.
 * При переходе на PostgreSQL заменяется на JPA-реализацию без изменения бизнес-логики
 * (подключается через Spring profile или условный бин).
 *
 * <h3>Потокобезопасность</h3>
 * {@link ConcurrentHashMap} гарантирует видимость между потоками.
 * Операция {@link #save} атомарна (put по ключу unitId).
 * Операция {@link #deactivateByUnitId} использует {@code computeIfPresent}
 * для атомарной read-modify-write.
 *
 * <h3>Память</h3>
 * Деактивированные уведомления сохраняются в map для истории (бесконечный рост отсутствует
 * благодаря toggle-семантике: деактивированное уведомление на том же unitId заменяется
 * новым активным при следующем toggle).
 */
@Component
public class InMemoryNotificationStore implements NotificationRepository {

    /**
     * unitId → последнее уведомление (активное или деактивированное).
     * Для поиска активного используется проверка {@code .active()}.
     */
    private final ConcurrentHashMap<String, ProductionNotification> store =
            new ConcurrentHashMap<>();

    @Override
    public @NonNull Optional<ProductionNotification> findActiveByUnitId(@NonNull String unitId) {
        ProductionNotification notification = store.get(unitId);
        if (notification != null && notification.active()) {
            return Optional.of(notification);
        }
        return Optional.empty();
    }

    @Override
    public @NonNull List<ProductionNotification> findAllActive() {
        return store.values().stream()
                .filter(ProductionNotification::active)
                .toList();
    }

    @Override
    public void save(@NonNull ProductionNotification notification) {
        store.put(notification.unitId(), notification);
    }

    @Override
    public void deactivateByUnitId(@NonNull String unitId) {
        store.computeIfPresent(unitId, (key, existing) -> {
            if (existing.active()) {
                return existing.deactivate();
            }
            return existing;
        });
    }
}
