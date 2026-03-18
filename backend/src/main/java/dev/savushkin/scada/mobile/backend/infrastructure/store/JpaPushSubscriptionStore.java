package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.application.ports.DbPushSubscriptionRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PushSubscriptionRepository;
import dev.savushkin.scada.mobile.backend.domain.model.PushSubscription;
import dev.savushkin.scada.mobile.backend.domain.model.PushSubscriptionEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Primary
public class JpaPushSubscriptionStore implements PushSubscriptionRepository {

    private final DbPushSubscriptionRepository repository;

    public JpaPushSubscriptionStore(DbPushSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(@NonNull PushSubscription subscription) {
        PushSubscriptionEntity entity = repository.findByInstallationId(subscription.installationId())
                .orElseGet(PushSubscriptionEntity::new);

        entity.setInstallationId(subscription.installationId());
        entity.setEndpoint(subscription.endpoint());
        entity.setP256dhKey(subscription.p256dhKey());
        entity.setAuthKey(subscription.authKey());
        entity.setPlatform(subscription.platform());
        entity.setAppChannel(subscription.appChannel());
        entity.setPreferredWorkshopId(subscription.preferredWorkshopId());
        entity.setPreferredUnitId(subscription.preferredUnitId());
        entity.setRegisteredAt(subscription.registeredAt());
        entity.setActive(subscription.active());

        repository.save(entity);
    }

    @Override
    public @NonNull Optional<PushSubscription> findByInstallationId(@NonNull String installationId) {
        return repository.findByInstallationId(installationId).map(this::toDomain);
    }

    @Override
    public boolean deactivate(@NonNull String installationId) {
        Optional<PushSubscriptionEntity> opt = repository.findByInstallationId(installationId);
        if (opt.isEmpty()) {
            return false;
        }
        PushSubscriptionEntity entity = opt.get();
        entity.setActive(false);
        repository.save(entity);
        return true;
    }

    @Override
    public @NonNull List<PushSubscription> findAllActive() {
        return repository.findAllByActiveTrue().stream().map(this::toDomain).toList();
    }

    private PushSubscription toDomain(PushSubscriptionEntity entity) {
        return new PushSubscription(
                entity.getInstallationId(),
                entity.getEndpoint(),
                entity.getP256dhKey(),
                entity.getAuthKey(),
                entity.getPlatform(),
                entity.getAppChannel(),
                entity.getPreferredWorkshopId(),
                entity.getPreferredUnitId(),
                entity.getRegisteredAt(),
                entity.isActive()
        );
    }
}
