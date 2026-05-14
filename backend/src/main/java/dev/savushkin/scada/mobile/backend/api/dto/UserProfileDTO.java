package dev.savushkin.scada.mobile.backend.api.dto;

import java.util.List;

public record UserProfileDTO(
        String fullName,
        String role,
        String workerCode,
        List<AssignedUnitDTO> assignedUnits
) {
}
