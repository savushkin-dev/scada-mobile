package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.config.jwt.JwtTokenProvider;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MachineRegistrationService {
    private final UnitJpaRepository unitRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public MachineRegistrationService(UnitJpaRepository unitRepository, JwtTokenProvider jwtTokenProvider) {
        this.unitRepository = unitRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public Optional<RegisteredMachine> register(String printSrvId) {
        return unitRepository.findByPrintsrvInstanceId(printSrvId.trim())
                .filter(UnitEntity::isActive)
                .filter(unit -> unit.getPrintsrvInstanceId() != null)
                .map(unit -> new RegisteredMachine(
                        jwtTokenProvider.generateAutoProvisionedMachineToken(printSrvId.trim(), unit.getId()),
                        unit.getId(),
                        printSrvId.trim()));
    }

    public record RegisteredMachine(JwtTokenProvider.MachineToken token, long unitId, String printSrvId) {
    }
}
