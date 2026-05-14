package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.AuthUser;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Port for resolving users by credentials.
 */
public interface UserAuthRepository {

    /**
     * Finds user by worker code and password.
     *
     * @param code     worker code
     * @param password worker password
     * @return optional user
     */
    @NonNull Optional<AuthUser> findByCredentials(@NonNull String code, @NonNull String password);
}
