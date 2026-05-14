package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Domain model for authenticated user identity.
 */
public record AuthUser(
        long id,
        String code,
        String fullName,
        boolean active
) {
}
