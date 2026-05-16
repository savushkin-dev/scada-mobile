package dev.savushkin.scada.mobile.backend.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Провайдер JWT-токенов: генерация access/refresh и валидация.
 * <p>
 * Access-токен — подписанный JWT с claims (sub, role, exp).
 * Refresh-токен — криптостойкий случайный UUID, хранится в БД (хэш).
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    // ── Access Token ──────────────────────────────────────────────────────

    /**
     * Генерирует access-токен (JWT) для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param role   роль пользователя
     * @return подписанный JWT
     */
    public @NonNull String generateAccessToken(long userId, @NonNull String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessExpirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getAccessKey())
                .compact();
    }

    /**
     * Валидирует access-токен и возвращает userId.
     *
     * @param token JWT access-токен
     * @return userId или null если токен невалиден/истёк
     */
    public @Nullable Long validateAccessToken(@NonNull String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getAccessKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return parseUserId(claims.getSubject());
        } catch (ExpiredJwtException e) {
            log.debug("Access token expired");
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid access token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Извлекает роль из access-токена без полной валидации подписи.
     * Используется только после успешной {@link #validateAccessToken}.
     */
    public @Nullable String extractRole(@NonNull String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getAccessKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("role", String.class);
        } catch (JwtException e) {
            return null;
        }
    }

    // ── Refresh Token ─────────────────────────────────────────────────────

    /**
     * Генерирует новый refresh-токен (случайный UUID).
     *
     * @return plaintext refresh-токен
     */
    public @NonNull String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Возвращает SHA-256 хэш refresh-токена для хранения в БД.
     */
    public @NonNull String hashRefreshToken(@NonNull String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private SecretKey getAccessKey() {
        String secret = jwtProperties.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("JWT access secret is not configured — using dev fallback. "
                    + "Set JWT_ACCESS_SECRET env var for production!");
            // Dev fallback: генерируем детерминированный ключ из дефолтной строки.
            // В продакшене это НЕБЕЗОПАСНО — обязательно задавать JWT_ACCESS_SECRET.
            secret = "scada-mobile-dev-secret-key-do-not-use-in-production";
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private @Nullable Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) return null;
        try {
            return Long.parseLong(subject.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
