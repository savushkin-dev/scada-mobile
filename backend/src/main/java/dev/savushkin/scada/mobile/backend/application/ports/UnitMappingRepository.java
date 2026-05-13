package dev.savushkin.scada.mobile.backend.application.ports;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

public interface UnitMappingRepository {

    @NonNull Optional<String> findPrintSrvInstanceIdByUnitId(long unitId);

    @NonNull Optional<Long> findUnitIdByPrintSrvInstanceId(@NonNull String printsrvInstanceId);
}
