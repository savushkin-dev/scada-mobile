package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.RefreshTokenRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RefreshTokenEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.RefreshTokenJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class RefreshTokenJpaAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;

    public RefreshTokenJpaAdapter(RefreshTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public @NonNull RefreshTokenEntity save(@NonNull RefreshTokenEntity entity) {
        return repository.save(entity);
    }

    @Override
    public @NonNull Optional<RefreshTokenEntity> findByTokenHash(@NonNull String tokenHash) {
        return repository.findByTokenHash(tokenHash);
    }

    @Override
    @Transactional
    public void revokeAllByUserId(long userId) {
        repository.revokeAllByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteExpired(@NonNull Instant before) {
        repository.deleteByExpiresAtBefore(before);
    }
}
