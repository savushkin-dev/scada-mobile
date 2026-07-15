package dev.savushkin.scada.mobile.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequestDTO(
        @NotBlank String newPassword
) {
}
