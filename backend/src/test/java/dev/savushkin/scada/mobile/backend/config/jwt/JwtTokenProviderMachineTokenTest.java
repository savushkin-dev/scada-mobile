package dev.savushkin.scada.mobile.backend.config.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты генерации и валидации machine-JWT (автоматы / СКАДА).
 */
class JwtTokenProviderMachineTokenTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessSecret(SECRET);
        properties.setAccessExpirationMinutes(60);
        properties.setMachineTokenExpirationDays(365);
        tokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void machineTokenCarriesMachineClaims() {
        JwtTokenProvider.MachineToken machineToken =
                tokenProvider.generateMachineToken("hassia1", 30);

        Claims claims = tokenProvider.validateAccessTokenClaims(machineToken.token());

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("hassia1");
        assertThat(claims.get(JwtTokenProvider.SUBJECT_TYPE_CLAIM, String.class))
                .isEqualTo(JwtTokenProvider.SUBJECT_TYPE_MACHINE);
        assertThat(claims.get("role", String.class)).isEqualTo("MACHINE");
        assertThat(claims.getId()).isEqualTo(machineToken.jti());
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void userTokenCarriesUserSubjectType() {
        String token = tokenProvider.generateAccessToken(42L, "OPERATOR");

        Claims claims = tokenProvider.validateAccessTokenClaims(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtTokenProvider.SUBJECT_TYPE_CLAIM, String.class))
                .isEqualTo(JwtTokenProvider.SUBJECT_TYPE_USER);
        assertThat(tokenProvider.validateAccessToken(token)).isEqualTo(42L);
    }

    @Test
    void machineTokenIsNotParsedAsUserId() {
        JwtTokenProvider.MachineToken machineToken =
                tokenProvider.generateMachineToken("hassia1", 30);

        // sub нечисловой — validateAccessToken (userId) обязан вернуть null
        assertThat(tokenProvider.validateAccessToken(machineToken.token())).isNull();
    }

    @Test
    void invalidTokenIsRejected() {
        assertThat(tokenProvider.validateAccessTokenClaims("not-a-token")).isNull();
    }
}
