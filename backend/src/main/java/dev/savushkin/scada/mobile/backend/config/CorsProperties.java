package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Конфиг CORS, управляемый через application-*.yaml.
 *
 * <p>Важно: в prod не используем wildcard, держим строгий allowlist origins.
 */
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    private Policy policy = new Policy();

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public static class Policy {
        /**
         * Разрешённые origins (scheme+host+port).
         * Примеры: <a href="http://127.0.0.1:5500">...</a>, <a href="http://localhost:5500">...</a>
         */
        private List<String> allowedOrigins = List.of();

        /**
         * Разрешённые методы (минимизируем список).
         */
        private List<String> allowedMethods = List.of("GET", "POST", "OPTIONS");

        /**
         * Разрешённые заголовки запроса.
         * Для простого API обычно достаточно Content-Type и Authorization (если появится).
         */
        private List<String> allowedHeaders = List.of("Content-Type", "Authorization");

        /**
         * Заголовки ответа, которые браузер может читать из JS.
         */
        private List<String> exposedHeaders = List.of();

        /**
         * Нужно включать только если реально используете cookie-based auth.
         * При allowCredentials=true запрещено использовать wildcard origins.
         */
        private boolean allowCredentials = false;

        /**
         * Cache preflight ответа в браузере.
         */
        private long maxAgeSeconds = 3600;

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }
}

