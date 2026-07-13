package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.Optional;


public interface DeviceTypeJpaRepository extends JpaRepository<DeviceTypeEntity, Long> {

    @RestResource(exported = false)
    @NonNull Optional<DeviceTypeEntity> findByCode(@NonNull String code);

    @RestResource(exported = false)
    @NonNull Optional<DeviceTypeEntity> findByName(@NonNull String name);
}
