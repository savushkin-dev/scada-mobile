package dev.savushkin.scada.mobile.backend.config;

import jakarta.servlet.Filter;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Регистрирует {@link CorsFilter} как Servlet Filter с высоким приоритетом.
 * <p>
 * Spring {@link org.springframework.web.servlet.config.annotation.CorsRegistry}
 * (через {@link CorsConfig}) работает на уровне HandlerMapping и НЕ добавляет
 * CORS-заголовки к ответам, сформированным до достижения контроллера — например,
 к 401 от {@link JwtAuthenticationFilter}.
 * <p>
 * Servlet Filter с {@link Ordered#HIGHEST_PRECEDENCE} гарантирует, что CORS-
 * preflight и заголовки ответа обрабатываются ДО JWT-фильтра, устраняя
 * ложные CORS-ошибки в браузере при истёкшем/отсутствующем токене.
 */
@Configuration
public class CorsFilterConfig {

    private final CorsProperties corsProperties;

    public CorsFilterConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public FilterRegistrationBean<Filter> corsFilterRegistration() {
        CorsProperties.Policy policy = corsProperties.getPolicy();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(policy.getAllowedOrigins());
        config.setAllowedOriginPatterns(policy.getAllowedOriginPatterns());
        config.setAllowedMethods(policy.getAllowedMethods());
        config.setAllowedHeaders(policy.getAllowedHeaders());
        config.setExposedHeaders(mergeWithETag(policy.getExposedHeaders()));
        config.setAllowCredentials(policy.isAllowCredentials());
        config.setMaxAge(policy.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Применяем ко ВСЕМ путям — /api/** и /ws/**
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
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
