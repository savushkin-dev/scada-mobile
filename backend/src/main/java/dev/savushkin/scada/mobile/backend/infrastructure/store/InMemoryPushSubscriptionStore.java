package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.application.ports.PushSubscriptionRepository;
import dev.savushkin.scada.mobile.backend.domain.model.PushSubscription;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация {@link PushSubscriptionRepository}.
 * <p>
 * Достаточна для MVP-запуска и локального тестирования. В production-окружении
 * с несколькими инстансами backend или после рестарта подписки будут потеряны —
 * необходима замена на DB-backed реализацию.
 * <p>
 * Хранение: {@link ConcurrentHashMap} с ключом {@code installationId}.
 * Все мутирующие методы синхронизированы на уровне записи через атомарные операции map.
 */
@Component
public class InMemoryPushSubscriptionStore implements PushSubscriptionRepository {

    private final ConcurrentHashMap<String, PushSubscription> store = new ConcurrentHashMap<>();

    @Override
    public void save(@NonNull PushSubscription subscription) {
        store.put(subscription.installationId(), subscription);
    }

    @Override
    public @NonNull Optional<PushSubscription> findByInstallationId(@NonNull String installationId) {
        return Optional.ofNullable(store.get(installationId));
    }

    @Override
    public boolean deactivate(@NonNull String installationId) {
        PushSubscription existing = store.get(installationId);
        if (existing == null) {
            return false;
        }
        // Создаём новую запись с active = false, сохраняя остальные поля.
        PushSubscription deactivated = new PushSubscription(
                existing.installationId(),
                existing.endpoint(),
                existing.p256dhKey(),
                existing.authKey(),
                existing.platform(),
                existing.appChannel(),
                existing.preferredWorkshopId(),
                existing.preferredUnitId(),
                existing.registeredAt(),
                false
        );
        store.put(installationId, deactivated);
        return true;
    }

    @Override
    public @NonNull List<PushSubscription> findAllActive() {
        return store.values().stream()
                .filter(PushSubscription::active)
                .toList();
    }
}
