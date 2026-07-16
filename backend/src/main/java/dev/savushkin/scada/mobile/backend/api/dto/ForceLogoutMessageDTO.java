package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: принудительный разлогин пользователя.
 * Отправляется, например, когда администратор деактивирует сотрудника.
 */
public record ForceLogoutMessageDTO(String type, String reason) {

    public static final String TYPE = "FORCE_LOGOUT";

    public static ForceLogoutMessageDTO of(String reason) {
        return new ForceLogoutMessageDTO(TYPE, reason);
    }
}
