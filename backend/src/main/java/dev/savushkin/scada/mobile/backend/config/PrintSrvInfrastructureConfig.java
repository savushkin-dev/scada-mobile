package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Активирует типизированные {@link ConfigurationProperties}-классы PrintSrv-инфраструктуры.
 *
 * <p>Разделено от {@code Application.java}, чтобы можно было тестировать бины конфигурации
 * изолированно через {@code @SpringBootTest(classes = PrintSrvInfrastructureConfig.class)}.
 */
@Configuration
@EnableConfigurationProperties(PrintSrvProperties.class)
public class PrintSrvInfrastructureConfig {
    // Этот класс — только декларация; логики здесь нет.
}
