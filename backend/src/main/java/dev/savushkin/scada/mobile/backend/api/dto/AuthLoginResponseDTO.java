package dev.savushkin.scada.mobile.backend.api.dto;

import org.jspecify.annotations.Nullable;

public record AuthLoginResponseDTO(
        String status,
        @Nullable String userId,
        @Nullable String code,
        @Nullable String fullName,
        @Nullable String role,
        boolean temporaryPassword,
        @Nullable String accessToken,
        @Nullable String refreshToken
) {
    public static AuthLoginResponseDTO success(String userId, String code, String fullName, String role,
                                                boolean temporaryPassword, String accessToken, String refreshToken) {
        return new AuthLoginResponseDTO("ok", userId, code, fullName, role, temporaryPassword, accessToken, refreshToken);
    }
}
