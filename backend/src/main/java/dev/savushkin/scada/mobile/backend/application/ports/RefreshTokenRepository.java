package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RefreshTokenEntity;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Optional;

/**
 * Порт хранения refresh-токенов.
 */
public interface RefreshTokenRepository {

    @NonNull RefreshTokenEntity save(@NonNull RefreshTokenEntity entity);

    @NonNull Optional<RefreshTokenEntity> findByTokenHash(@NonNull String tokenHash);

    void revokeAllByUserId(long userId);

    void deleteExpired(@NonNull Instant before);
}
