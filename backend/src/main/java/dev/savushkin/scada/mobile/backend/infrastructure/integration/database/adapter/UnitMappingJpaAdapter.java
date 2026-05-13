package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.UnitMappingRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UnitMappingJpaAdapter implements UnitMappingRepository {

    private final UnitJpaRepository unitRepository;

    public UnitMappingJpaAdapter(UnitJpaRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    @Override
    public @NonNull Optional<String> findPrintSrvInstanceIdByUnitId(long unitId) {
        return unitRepository.findPrintsrvInstanceIdById(unitId);
    }

    @Override
    public @NonNull Optional<Long> findUnitIdByPrintSrvInstanceId(@NonNull String printsrvInstanceId) {
        return unitRepository.findUnitIdByPrintsrvInstanceId(printsrvInstanceId);
    }
}
