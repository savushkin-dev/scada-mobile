package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCatalogJpaRepository extends JpaRepository<DeviceCatalogEntity, Long> {
}
