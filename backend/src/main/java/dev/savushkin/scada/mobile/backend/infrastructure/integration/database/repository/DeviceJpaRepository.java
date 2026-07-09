package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;

public interface DeviceJpaRepository extends JpaRepository<DeviceEntity, Long> {

    @RestResource(exported = false)
    List<DeviceEntity> findByUnit_Id(Long unitId);

    @RestResource(exported = false)
    void deleteByUnit_Id(Long unitId);

    @RestResource(exported = false)
    boolean existsByUnit_IdAndCatalog_Id(Long unitId, Long catalogId);

    @RestResource(exported = false)
    @Query("""
            select d
            from DeviceEntity d
            join fetch d.catalog c
            join fetch c.type
            where d.unit.printsrvInstanceId = :printsrvInstanceId
            """)
    List<DeviceEntity> findByUnit_PrintsrvInstanceId(@Param("printsrvInstanceId") String printsrvInstanceId);
}
