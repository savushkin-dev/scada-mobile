package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;


public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

    @NonNull Optional<RoleEntity> findByName(@NonNull String name);
}
