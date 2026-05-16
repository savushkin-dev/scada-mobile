package dev.savushkin.scada.mobile.backend.config.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация JWT-токенов.
 * <p>
 * Секция {@code jwt} в {@code application.yaml}.
 * <p>
 * Для продакшена обязательно задавать {@code access-secret} и {@code refresh-secret}
 * через переменные окружения (минимум 256 бит в Base64).
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String accessSecret = "";
    private String refreshSecret = "";
    private long accessExpirationMinutes = 15;
    private long refreshExpirationDays = 7;

    public String getAccessSecret() { return accessSecret; }
    public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }

    public String getRefreshSecret() { return refreshSecret; }
    public void setRefreshSecret(String refreshSecret) { this.refreshSecret = refreshSecret; }

    public long getAccessExpirationMinutes() { return accessExpirationMinutes; }
    public void setAccessExpirationMinutes(long accessExpirationMinutes) { this.accessExpirationMinutes = accessExpirationMinutes; }

    public long getRefreshExpirationDays() { return refreshExpirationDays; }
    public void setRefreshExpirationDays(long refreshExpirationDays) { this.refreshExpirationDays = refreshExpirationDays; }
}
