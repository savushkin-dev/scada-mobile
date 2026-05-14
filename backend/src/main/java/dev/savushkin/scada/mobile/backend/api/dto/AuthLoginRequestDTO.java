package dev.savushkin.scada.mobile.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthLoginRequestDTO(
        @NotBlank @Size(max = 10) String workerCode,
        @NotBlank @Size(max = 10) String password
) {
}
