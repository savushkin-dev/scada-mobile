package dev.savushkin.scada.mobile.backend.api.dto;

public record MachineRegisterResponseDTO(
        String token,
        String printSrvId,
        long unitId,
        String expiresAt
) {
}
