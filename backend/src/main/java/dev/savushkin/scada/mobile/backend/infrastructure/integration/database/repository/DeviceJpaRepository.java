package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;


public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, Long> {

    @RestResource(exported = false)
    List<DeviceEntity> findByUnit_Id(Long unitId);
}
