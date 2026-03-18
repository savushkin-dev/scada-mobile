package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация Web Push / VAPID.
 * <p>
 * Регистрирует {@link VapidProperties} как типизированный конфигурационный бин,
 * связанный с префиксом {@code vapid} из {@code application.yaml}.
 * <p>
 * Фактическая инициализация {@link nl.martijndwars.webpush.PushService} выполняется
 * в {@link dev.savushkin.scada.mobile.backend.infrastructure.notification.WebPushNotificationService}
 * через {@code @PostConstruct}, чтобы обеспечить fail-safe поведение:
 * если ключи не заданы — сервис просто отключает Web Push без выброса исключения на старте.
 */
@Configuration
@EnableConfigurationProperties(VapidProperties.class)
public class VapidConfig {
}
