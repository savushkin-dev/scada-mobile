package dev.savushkin.scada.mobile.backend.api.dto;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.AdminNotificationEntity;
import org.jspecify.annotations.NonNull;

/**
 * WebSocket-сообщение типа {@code ADMIN_NOTIFICATION}.
 * <p>
 * Рассылается администраторам при автоматических событиях:
 * обнаружение/отключение/переподключение устройства.
 *
 * @param type             всегда {@code "ADMIN_NOTIFICATION"}
 * @param notificationType тип уведомления: {@code DEVICE_DISCOVERED}, {@code DEVICE_DISCONNECTED}, {@code DEVICE_RECONNECTED}
 * @param severity         {@code INFO} или {@code WARNING}
 * @param instanceId       идентификатор аппарата
 * @param deviceCode       код устройства (может быть null)
 * @param message          человекочитаемое сообщение
 * @param timestamp        ISO-8601 UTC момент создания
 */
public record AdminNotificationMessageDTO(
        @NonNull String type,
        @NonNull String notificationType,
        @NonNull String severity,
        @NonNull String instanceId,
        String deviceCode,
        @NonNull String message,
        @NonNull String timestamp
) {

    public static @NonNull AdminNotificationMessageDTO from(@NonNull AdminNotificationEntity entity) {
        return new AdminNotificationMessageDTO(
                "ADMIN_NOTIFICATION",
                entity.getType().name(),
                entity.getSeverity().name(),
                entity.getInstanceId(),
                entity.getDeviceCode(),
                entity.getMessage(),
                entity.getCreatedAt().toString()
        );
    }
}
