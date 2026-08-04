package dev.savushkin.scada.mobile.backend.api.dto;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.AdminNotificationEntity;
import org.jspecify.annotations.NonNull;

/**
 * WebSocket-сообщение типа {@code ADMIN_NOTIFICATION}.
 * <p>
 * Рассылается администраторам при автоматических событиях:
 * обнаружение/отключение/переподключение устройства,
 * смена пароля сотрудника, длительное бездействие сотрудника.
 *
 * @param type             всегда {@code "ADMIN_NOTIFICATION"}
 * @param notificationType тип уведомления: {@code DEVICE_DISCOVERED}, {@code DEVICE_DISCONNECTED},
 *                         {@code DEVICE_RECONNECTED}, {@code PASSWORD_CHANGED}, {@code USER_INACTIVE}
 * @param severity         {@code INFO} или {@code WARNING}
 * @param instanceId       идентификатор аппарата (null у событий о сотрудниках)
 * @param deviceCode       код устройства (может быть null)
 * @param userId           идентификатор сотрудника (может быть null, заполнен у событий о сотрудниках)
 * @param message          человекочитаемое сообщение
 * @param timestamp        ISO-8601 UTC момент создания
 */
public record AdminNotificationMessageDTO(
        @NonNull String type,
        @NonNull String notificationType,
        @NonNull String severity,
        String instanceId,
        String deviceCode,
        Long catalogId,
        Long userId,
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
                entity.getCatalogId(),
                entity.getUserId(),
                entity.getMessage(),
                entity.getCreatedAt().toString()
        );
    }
}
