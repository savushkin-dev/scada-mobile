package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

public interface DeviceCatalogJpaRepository extends JpaRepository<DeviceCatalogEntity, Long>, JpaSpecificationExecutor<DeviceCatalogEntity> {

    @NonNull Optional<DeviceCatalogEntity> findByCode(@NonNull String code);

    @NonNull Optional<DeviceCatalogEntity> findByName(@NonNull String name);
}
