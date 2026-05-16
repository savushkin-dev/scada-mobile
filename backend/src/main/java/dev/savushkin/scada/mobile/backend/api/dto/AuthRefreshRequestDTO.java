package dev.savushkin.scada.mobile.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на обновление access-токена через refresh-токен.
 */
public record AuthRefreshRequestDTO(
        @NotBlank String refreshToken
) {
}
