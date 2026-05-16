package dev.savushkin.scada.mobile.backend.config.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Активирует типизированный {@link JwtProperties}-класс для JWT-конфигурации.
 * <p>
 * Паттерн аналогичен {@link PrintSrvInfrastructureConfig} — изолированная декларация
 * для возможности тестирования бинов конфигурации.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtInfrastructureConfig {
    // Этот класс — только декларация; логики здесь нет.
}
