package dev.savushkin.scada.mobile.backend.config.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Валидатор audience для JWT токенов.
 * <p>
 * Проверяет, что токен содержит ожидаемую audience в списке {@code aud} claim.
 * Используется в {@link dev.savushkin.scada.mobile.backend.config.SecurityConfig#jwtDecoder}.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    public static AudienceValidator of(String audience) {
        return new AudienceValidator(audience);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Invalid audience", null)
        );
    }
}
