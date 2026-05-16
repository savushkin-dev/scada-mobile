package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Ответ на запрос обновления токенов.
 */
public record AuthRefreshResponseDTO(
        String accessToken,
        String refreshToken
) {
}
