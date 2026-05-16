package dev.savushkin.scada.mobile.backend.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
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
 * Провайдер JWT-токенов: генерация access/refresh.
 * <p>
 * Валидация access-токена для HTTP-запросов теперь выполняется Spring Security
 * Resource Server через {@link dev.savushkin.scada.mobile.backend.config.SecurityConfig#jwtDecoder}.
 * <p>
 * Этот класс отвечает за <strong>создание</strong> токенов, а также за
 * валидацию WebSocket handshake токенов (где Spring Security filter chain
 * не применяется).
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
                .id(UUID.randomUUID().toString())
                .issuer("scada-mobile")
                .audience().add("scada-mobile-api").and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getAccessKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Валидирует access-токен и возвращает userId.
     * <p>
     * Используется только для WebSocket handshake (где Spring Security
     * Resource Server не обрабатывает запрос).
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

    private @Nullable Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) return null;
        try {
            return Long.parseLong(subject.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SecretKey getAccessKey() {
        String secret = jwtProperties.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT access secret is not configured. " +
                    "Set JWT_ACCESS_SECRET environment variable."
            );
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
