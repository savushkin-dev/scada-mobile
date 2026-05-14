package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.UnitQueryRepository;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSummary;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UnitQueryJpaAdapter implements UnitQueryRepository {

    private final UnitJpaRepository unitRepository;

    public UnitQueryJpaAdapter(UnitJpaRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    @Override
    public @NonNull List<UnitSummary> findAllActiveUnits() {
        return unitRepository.findAllActiveUnits()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public @NonNull Optional<UnitSummary> findActiveUnitById(long unitId) {
        return unitRepository.findActiveUnitById(unitId)
                .map(this::toSummary);
    }

    private UnitSummary toSummary(UnitEntity entity) {
        return new UnitSummary(entity.getId(), entity.getName());
    }
}
