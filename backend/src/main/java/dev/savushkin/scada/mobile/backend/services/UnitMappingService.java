package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.UnitMappingRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UnitMappingService {

    private final UnitMappingRepository unitMappingRepository;

    public UnitMappingService(UnitMappingRepository unitMappingRepository) {
        this.unitMappingRepository = unitMappingRepository;
    }

    public @NonNull Optional<String> findPrintSrvInstanceId(long unitId) {
        return unitMappingRepository.findPrintSrvInstanceIdByUnitId(unitId);
    }

    public @NonNull Optional<Long> findUnitIdByPrintSrvInstanceId(@NonNull String printsrvInstanceId) {
        return unitMappingRepository.findUnitIdByPrintSrvInstanceId(printsrvInstanceId);
    }
}
