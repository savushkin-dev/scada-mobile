package dev.savushkin.scada.mobile.backend.api.dto;

import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record NotificationWorkflowResponseDTO(
        Long notificationId,
        String unitId,
        String creatorId,
        String status,
        @Nullable String acceptedBy,
        @Nullable Instant acceptedAt,
        @Nullable Instant completedAt,
        @Nullable Instant cancelledAt,
        long version
) {
    public static NotificationWorkflowResponseDTO from(ProductionNotification notification) {
        return new NotificationWorkflowResponseDTO(
                notification.notificationId(),
                notification.unitId(),
                notification.creatorId(),
                notification.status().name(),
                notification.acceptedBy(),
                notification.acceptedAt(),
                notification.completedAt(),
                notification.cancelledAt(),
                notification.version());
    }
}
