package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Активирует типизированный {@link NotificationProperties}-класс для уведомлений.
 * <p>
 * Паттерн аналогичен {@link PrintSrvInfrastructureConfig} — изолированная декларация
 * для возможности тестирования бинов конфигурации через
 * {@code @SpringBootTest(classes = NotificationInfrastructureConfig.class)}.
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationInfrastructureConfig {
    // Этот класс — только декларация; логики здесь нет.
}
