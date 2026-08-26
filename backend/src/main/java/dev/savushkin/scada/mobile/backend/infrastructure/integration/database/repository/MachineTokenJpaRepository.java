package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.MachineTokenEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.Optional;

public interface MachineTokenJpaRepository extends JpaRepository<MachineTokenEntity, Long> {

    @RestResource(exported = false)
    @NonNull Optional<MachineTokenEntity> findByJti(@NonNull String jti);
}
