package dev.savushkin.scada.mobile.backend.api.dto;

import org.jspecify.annotations.Nullable;

/**
 * DTO ответа на toggle-операцию уведомления (POST /api/line/{unitId}/last-batch).
 *
 * @param status    Результат операции:
 *                  "activated" — уведомление создано,
 *                  "deactivated" — уведомление снято,
 *                  "already_active" — уведомление уже активно другим работником.
 * @param unitId    Идентификатор аппарата.
 * @param creatorId Идентификатор создателя (заполнен при activated/already_active).
 * @param timestamp ISO-8601 время операции (заполнен при activated/deactivated).
 */
public record NotificationToggleResponseDTO(
        String status,
        String unitId,
        @Nullable String creatorId,
        @Nullable String timestamp
) {
    public static NotificationToggleResponseDTO activated(String unitId, String creatorId, String timestamp) {
        return new NotificationToggleResponseDTO("activated", unitId, creatorId, timestamp);
    }

    public static NotificationToggleResponseDTO deactivated(String unitId) {
        return new NotificationToggleResponseDTO("deactivated", unitId, null, null);
    }

    public static NotificationToggleResponseDTO alreadyActive(String unitId, String existingCreatorId) {
        return new NotificationToggleResponseDTO("already_active", unitId, existingCreatorId, null);
    }
}
