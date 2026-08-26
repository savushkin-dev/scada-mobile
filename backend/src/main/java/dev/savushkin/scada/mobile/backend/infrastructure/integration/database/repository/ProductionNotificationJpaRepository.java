package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.ProductionNotificationEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;
import java.util.Optional;

public interface ProductionNotificationJpaRepository extends JpaRepository<ProductionNotificationEntity, Long> {

    @RestResource(exported = false)
    @NonNull Optional<ProductionNotificationEntity> findByUnitId(@NonNull Long unitId);

    @RestResource(exported = false)
    @NonNull List<ProductionNotificationEntity> findAllByActiveTrue();
}
