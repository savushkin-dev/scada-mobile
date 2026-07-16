package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменились настройки уведомлений пользователя.
 */
public record UserNotificationSettingsChangedMessageDTO(String type, UserNotificationSettingsPayload payload, String action) {

    public static final String TYPE = "USER_NOTIFICATION_SETTINGS_CHANGED";

    public record UserNotificationSettingsPayload(
            Long id,
            Long userId,
            Long unitId,
            boolean incidentNotificationsEnabled,
            boolean androidCallNotificationsEnabled,
            boolean active
    ) {
    }

    public static UserNotificationSettingsChangedMessageDTO of(UserNotificationSettingsPayload payload, String action) {
        return new UserNotificationSettingsChangedMessageDTO(TYPE, payload, action);
    }
}
