package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменилась роль.
 */
public record RoleChangedMessageDTO(String type, RolePayload payload, String action) {

    public static final String TYPE = "ROLE_CHANGED";

    public record RolePayload(Long id, String name) {
    }

    public static RoleChangedMessageDTO of(RolePayload payload, String action) {
        return new RoleChangedMessageDTO(TYPE, payload, action);
    }
}
