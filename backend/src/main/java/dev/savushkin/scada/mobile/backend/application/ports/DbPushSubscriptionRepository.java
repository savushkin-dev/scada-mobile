package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DbPushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, String> {
    Optional<PushSubscriptionEntity> findByInstallationId(String installationId);
    List<PushSubscriptionEntity> findAllByActiveTrue();
}
