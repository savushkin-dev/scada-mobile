package dev.savushkin.scada.mobile.backend.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Глобальная CORS-конфигурация.
 * <p>
 * Политика задаётся через properties (см. {@link CorsProperties}) и отличается по профилям.
 * В prod держим строгий allowlist origins, в dev можно разрешить локальные origin'ы фронта.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        CorsProperties.Policy policy = corsProperties.getPolicy();

        registry.addMapping("/api/**")
                .allowedOrigins(policy.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods(policy.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(policy.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(policy.getExposedHeaders().toArray(String[]::new))
                .allowCredentials(policy.isAllowCredentials())
                .maxAge(policy.getMaxAgeSeconds());
    }
}
