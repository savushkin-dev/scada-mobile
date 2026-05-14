package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.List;

/**
 * Доменная модель профиля пользователя для UI.
 */
public record UserProfile(
        long id,
        String code,
        String fullName,
        String role,
        boolean active,
        List<AssignedUnit> assignedUnits
) {
}
