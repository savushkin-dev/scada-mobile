package dev.savushkin.scada.mobile.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsUpdateDTO(
        @NotBlank String unitId,
        @NotNull Boolean techEnabled,
        @NotNull Boolean masterEnabled
) {
}
