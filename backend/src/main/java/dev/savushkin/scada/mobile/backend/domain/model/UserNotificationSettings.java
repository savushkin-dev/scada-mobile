package dev.savushkin.scada.mobile.backend.domain.model;

import java.time.LocalDateTime;

public record UserNotificationSettings(
        Long id,
        long userId,
        long unitId,
    boolean incidentNotificationsEnabled,
    boolean androidCallNotificationsEnabled,
        boolean active,
        LocalDateTime updatedAt
) {
    public UserNotificationSettings withUpdatedAt(LocalDateTime updatedAt) {
        return new UserNotificationSettings(
                id,
                userId,
                unitId,
            incidentNotificationsEnabled,
            androidCallNotificationsEnabled,
                active,
                updatedAt
        );
    }
}
