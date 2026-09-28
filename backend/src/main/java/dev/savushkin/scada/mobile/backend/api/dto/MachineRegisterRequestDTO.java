package dev.savushkin.scada.mobile.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

public record MachineRegisterRequestDTO(
        @NotBlank(message = "printSrvId обязателен")
        String printSrvId
) {
}
