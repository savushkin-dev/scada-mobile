package dev.savushkin.scada.mobile.backend.api.dto;

public record NotificationSettingDTO(
        String unitId,
        String unitName,
        boolean techEnabled,
        boolean masterEnabled
) {
}
