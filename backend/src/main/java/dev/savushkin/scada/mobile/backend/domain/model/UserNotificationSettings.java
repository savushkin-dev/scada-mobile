package dev.savushkin.scada.mobile.backend.domain.model;

import java.time.LocalDateTime;

public record UserNotificationSettings(
        Long id,
        long userId,
        long unitId,
        boolean systemSoundEnabled,
        boolean systemVibrationEnabled,
        boolean androidPushEnabled,
        boolean active,
        LocalDateTime updatedAt
) {
    public UserNotificationSettings withUpdatedAt(LocalDateTime updatedAt) {
        return new UserNotificationSettings(
                id,
                userId,
                unitId,
                systemSoundEnabled,
                systemVibrationEnabled,
                androidPushEnabled,
                active,
                updatedAt
        );
    }
}
