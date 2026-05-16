package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.RefreshTokenRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UserAuthRepository;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtTokenProvider;
import dev.savushkin.scada.mobile.backend.domain.model.AuthUser;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RefreshTokenEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final UserAuthRepository userAuthRepository;
    private final UserJpaRepository userJpaRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserAuthRepository userAuthRepository,
                       UserJpaRepository userJpaRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtTokenProvider jwtTokenProvider) {
        this.userAuthRepository = userAuthRepository;
        this.userJpaRepository = userJpaRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public @NonNull AuthUser authenticate(@NonNull String workerCode, @NonNull String password) {
        String code = normalize(workerCode);
        String pass = normalize(password);
        if (code.isEmpty() || pass.isEmpty()) {
            throw new InvalidCredentialsException(code);
        }

        AuthUser user = userAuthRepository.findByCredentials(code, pass)
                .orElseThrow(() -> new InvalidCredentialsException(code));

        if (!user.active()) {
            throw new UserInactiveException(user.id(), user.code());
        }

        return user;
    }

    /**
     * Создаёт новую пару access + refresh токенов для пользователя.
     */
    @Transactional
    public @NonNull TokenPair createTokenPair(@NonNull AuthUser user) {
        UserEntity userEntity = userJpaRepository.findByIdWithRole(user.id())
                .orElseThrow(() -> new InvalidCredentialsException(user.code()));

        String role = userEntity.getRole().getName();
        String accessToken = jwtTokenProvider.generateAccessToken(user.id(), role);

        String rawRefresh = jwtTokenProvider.generateRefreshToken();
        String refreshHash = jwtTokenProvider.hashRefreshToken(rawRefresh);

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUser(userEntity);
        entity.setTokenHash(refreshHash);
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);

        return new TokenPair(accessToken, rawRefresh);
    }

    /**
     * Обновляет пару токенов по refresh-токену (ротация).
     * Старый refresh-токен отзывается, создаётся новый.
     */
    @Transactional
    public @NonNull TokenPair rotateTokens(@NonNull String rawRefreshToken) {
        String hash = jwtTokenProvider.hashRefreshToken(rawRefreshToken);
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (existing.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token revoked");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        UserEntity user = existing.getUser();
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        String role = user.getRole().getName();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), role);

        String newRawRefresh = jwtTokenProvider.generateRefreshToken();
        String newRefreshHash = jwtTokenProvider.hashRefreshToken(newRawRefresh);

        RefreshTokenEntity newEntity = new RefreshTokenEntity();
        newEntity.setUser(user);
        newEntity.setTokenHash(newRefreshHash);
        newEntity.setCreatedAt(Instant.now());
        newEntity.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        newEntity.setRevoked(false);
        refreshTokenRepository.save(newEntity);

        return new TokenPair(accessToken, newRawRefresh);
    }

    /**
     * Отзывает все refresh-токены пользователя (logout).
     */
    @Transactional
    public void revokeAllRefreshTokens(long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }

    public static class InvalidCredentialsException extends RuntimeException {
        private final String code;

        public InvalidCredentialsException(String code) {
            super("Invalid credentials");
            this.code = code == null ? "" : code;
        }

        public String code() {
            return code;
        }
    }

    public static class UserInactiveException extends RuntimeException {
        private final long userId;
        private final String code;

        public UserInactiveException(long userId, String code) {
            super("User inactive");
            this.userId = userId;
            this.code = code == null ? "" : code;
        }

        public long userId() {
            return userId;
        }

        public String code() {
            return code;
        }
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
