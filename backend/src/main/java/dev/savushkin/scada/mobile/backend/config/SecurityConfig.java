package dev.savushkin.scada.mobile.backend.config;

import dev.savushkin.scada.mobile.backend.config.jwt.AudienceValidator;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtProperties;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Централизованная конфигурация безопасности Spring Security.
 * <p>
 * Заменяет самописный {@code JwtAuthenticationFilter} на стандартный стек:
 * <ul>
 *   <li>{@link SecurityFilterChain} — явная конфигурация endpoint'ов</li>
 *   <li>{@link JwtDecoder} — валидация JWT через Spring Security Resource Server</li>
 *   <li>{@link PasswordEncoder} — bcrypt для хеширования паролей</li>
 *   <li>{@link EnableMethodSecurity} — RBAC через аннотации</li>
 * </ul>
 * <p>
 * Архитектурные решения:
 * <ul>
 *   <li>Stateless — JWT, не HTTP sessions.</li>
 *   <li>CSRF отключён — Bearer token auth, не cookies.</li>
 *   <li>CORS настроен через {@link CorsConfigurationSource} внутри Security.</li>
 *   <li>JWT валидируется по signature, expiration, issuer, audience.</li>
 *   <li>Dev fallback для секрета УДАЛЁН — приложение падает на старте без {@code JWT_ACCESS_SECRET}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private static final String JWT_ISSUER = "scada-mobile";
    private static final String JWT_AUDIENCE = "scada-mobile-api";

    private final JwtProperties jwtProperties;

    public SecurityConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(@NonNull HttpSecurity http) throws Exception {
        http
            // Stateless JWT — не создаём сессии
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF не нужен при Bearer token auth (не cookies)
            .csrf(csrf -> csrf.disable())

            // Авторизация endpoint'ов
            .authorizeHttpRequests(auth -> auth
                // Публичные эндпоинты — без аутентификации
                // Используем /api/v1.0.0/... вместо /api/**/... т.к. ** в середине
                // не поддерживается в Spring Boot 4 / Spring Security 7
                .requestMatchers("/api/v1.0.0/auth/login",
                                 "/api/v1.0.0/auth/logout",
                                 "/api/v1.0.0/auth/refresh").permitAll()
                // Actuator health — для Kubernetes probes
                .requestMatchers("/actuator/health").permitAll()
                // WebSocket handshake — auth через отдельный interceptor
                .requestMatchers("/ws/**").permitAll()
                // Всё остальное — только с валидным JWT
                .anyRequest().authenticated()
            )

            // JWT Resource Server — валидация Bearer токенов
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder(jwtProperties))
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        String secret = jwtProperties.getAccessSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_ACCESS_SECRET env var is not set. " +
                "Generate with: openssl rand -base64 32"
            );
        }

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).build();

        // Валидаторы: issuer, audience, expiration, not-before
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(JWT_ISSUER);
        OAuth2TokenValidator<Jwt> withAudience = audienceValidator();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            withIssuer, withAudience
        ));

        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator() {
        return AudienceValidator.of(JWT_AUDIENCE);
    }

    private static List<String> mergeWithETag(List<String> configured) {
        if (configured.stream().noneMatch(h -> h.equalsIgnoreCase("ETag"))) {
            List<String> result = new java.util.ArrayList<>(configured);
            result.add("ETag");
            return result;
        }
        return configured;
    }
}
