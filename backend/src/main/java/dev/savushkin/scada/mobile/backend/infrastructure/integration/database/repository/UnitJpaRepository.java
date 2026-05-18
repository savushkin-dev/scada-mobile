package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface UnitJpaRepository extends JpaRepository<UnitEntity, Long> {

    @RestResource(exported = false)
    @NonNull Optional<UnitEntity> findByPrintsrvInstanceId(@NonNull String printsrvInstanceId);

    @RestResource(exported = false)
    @Query("""
            select u.printsrvInstanceId
            from UnitEntity u
            where u.active = true and u.printsrvInstanceId is not null
            """)
    @NonNull Set<String> findAllActivePrintsrvInstanceIds();

    @RestResource(exported = false)
    @Query("""
            select u.printsrvInstanceId
            from UnitEntity u
            where u.id = :unitId
            """)
    @NonNull Optional<String> findPrintsrvInstanceIdById(@Param("unitId") Long unitId);

    @RestResource(exported = false)
    @Query("""
            select u
            from UnitEntity u
            where u.active = true
            """)
    @NonNull List<UnitEntity> findAllActiveUnits();

    @RestResource(exported = false)
    @Query("""
            select u
            from UnitEntity u
            where u.active = true and u.id = :unitId
            """)
    @NonNull Optional<UnitEntity> findActiveUnitById(@Param("unitId") Long unitId);

    @RestResource(exported = false)
    @Query("""
            select u.id
            from UnitEntity u
            where u.printsrvInstanceId = :printsrvInstanceId
            """)
    @NonNull Optional<Long> findUnitIdByPrintsrvInstanceId(@Param("printsrvInstanceId") String printsrvInstanceId);
}
