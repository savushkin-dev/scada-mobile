package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.UnitSummary;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Порт чтения сведений об аппаратах.
 */
public interface UnitQueryRepository {

    @NonNull List<UnitSummary> findAllActiveUnits();

    @NonNull Optional<UnitSummary> findActiveUnitById(long unitId);
}
