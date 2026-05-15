package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменная модель закрепленного аппарата для профиля пользователя.
 */
public record AssignedUnit(
        long unitId,
        String unitName,
        String printsrvInstanceId
) {
}
