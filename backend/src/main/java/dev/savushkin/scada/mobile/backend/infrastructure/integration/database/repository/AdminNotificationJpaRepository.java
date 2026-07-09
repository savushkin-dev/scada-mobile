package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.AdminNotificationEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;

public interface AdminNotificationJpaRepository extends JpaRepository<AdminNotificationEntity, Long> {

    @RestResource(exported = false)
    @NonNull List<AdminNotificationEntity> findByReadFalseOrderByCreatedAtDesc();

    @RestResource(exported = false)
    @NonNull List<AdminNotificationEntity> findByInstanceIdOrderByCreatedAtDesc(@NonNull String instanceId);

    @RestResource(exported = false)
    long countByReadFalse();
}
