package dev.savushkin.scada.mobile.backend.api.dto;

public record AuthErrorResponseDTO(
        String status,
        String message
) {
    public static AuthErrorResponseDTO error(String message) {
        return new AuthErrorResponseDTO("error", message);
    }
}
