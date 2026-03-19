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

    /**
     * Добавляет {@code ETag} в список exposedHeaders, если его там ещё нет.
     * Браузер не пропустит заголовок к JS без явного указания в CORS.
     */
    private static String[] mergeWithETag(java.util.List<String> configured) {
        java.util.List<String> result = new java.util.ArrayList<>(configured);
        if (result.stream().noneMatch(h -> h.equalsIgnoreCase("ETag"))) {
            result.add("ETag");
        }
        return result.toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        CorsProperties.Policy policy = corsProperties.getPolicy();

        // Собираем exposedHeaders: объединяем из конфига с обязательным ETag
        // (нужен для кэширования topology-эндпоинтов на клиенте).
        String[] exposedHeaders = mergeWithETag(policy.getExposedHeaders());

        registry.addMapping("/api/**")
                .allowedOrigins(policy.getAllowedOrigins().toArray(String[]::new))
                .allowedOriginPatterns(policy.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(policy.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(policy.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(exposedHeaders)
                .allowCredentials(policy.isAllowCredentials())
                .maxAge(policy.getMaxAgeSeconds());
    }
}
