package dev.savushkin.scada.mobile.backend.api.dto;

import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * DTO производственного уведомления для workflow-endpoint'ов
 * (accept / complete / cancel, списки incoming / my-tasks / истории).
 * <p>
 * Помимо идентификаторов содержит читаемые имена (аппарат, создатель,
 * исполнитель), чтобы клиенту не требовались дополнительные запросы.
 */
public record NotificationWorkflowResponseDTO(
        Long notificationId,
        String unitId,
        @Nullable String unitName,
        String creatorId,
        @Nullable String creatorName,
        String status,
        Instant activatedAt,
        @Nullable String acceptedBy,
        @Nullable String acceptedByName,
        @Nullable Instant acceptedAt,
        @Nullable Instant completedAt,
        @Nullable Instant cancelledAt,
        long version
) {
    /**
     * Базовый маппинг без обогащения именами (unitName/creatorName/acceptedByName = null).
     */
    public static NotificationWorkflowResponseDTO from(ProductionNotification notification) {
        return from(notification, null, null, null);
    }

    /**
     * Маппинг с читаемыми именами аппарата, создателя и исполнителя.
     */
    public static NotificationWorkflowResponseDTO from(ProductionNotification notification,
                                                       @Nullable String unitName,
                                                       @Nullable String creatorName,
                                                       @Nullable String acceptedByName) {
        return new NotificationWorkflowResponseDTO(
                notification.notificationId(),
                notification.unitId(),
                unitName,
                notification.creatorId(),
                creatorName,
                notification.status().name(),
                notification.activatedAt(),
                notification.acceptedBy(),
                acceptedByName,
                notification.acceptedAt(),
                notification.completedAt(),
                notification.cancelledAt(),
                notification.version());
    }
}
