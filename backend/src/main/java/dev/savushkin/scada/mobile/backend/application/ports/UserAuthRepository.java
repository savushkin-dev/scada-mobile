package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.AuthUser;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Port for resolving users by worker code.
 */
public interface UserAuthRepository {

    /**
     * Finds user by worker code.
     *
     * @param code worker code
     * @return optional user with password hash
     */
    @NonNull Optional<AuthUserWithPassword> findByCode(@NonNull String code);

    /**
     * Domain model with password hash for authentication.
     */
    record AuthUserWithPassword(
            long id,
            @NonNull String code,
            @NonNull String fullName,
            boolean active,
            @NonNull String passwordHash
    ) {
    }
}
