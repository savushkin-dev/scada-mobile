package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменная модель настроек уведомлений по аппарату.
 */
public record UnitNotificationPreference(
        long unitId,
        String unitName,
        boolean techEnabled,
        boolean masterEnabled
) {
}
