package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.WorkshopEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;
import java.util.Optional;


public interface WorkshopJpaRepository extends JpaRepository<WorkshopEntity, Long> {

    @RestResource(exported = false)
    @NonNull List<WorkshopEntity> findByActiveTrue();

    @RestResource(exported = false)
    @NonNull Optional<WorkshopEntity> findByName(@NonNull String name);
}
