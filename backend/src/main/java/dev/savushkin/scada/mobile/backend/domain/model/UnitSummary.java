package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Короткая доменная модель аппарата для списков настроек.
 */
public record UnitSummary(
        long unitId,
        String unitName
) {
}
