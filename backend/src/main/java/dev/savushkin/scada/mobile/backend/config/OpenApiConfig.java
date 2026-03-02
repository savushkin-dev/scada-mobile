package dev.savushkin.scada.mobile.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация OpenAPI (Swagger) документации.
 * <p>
 * Определяет метаданные API: заголовок, описание, версию, контакты.
 * Документация доступна только в dev профиле (контролируется через application-dev.yaml).
 * <p>
 * Endpoints документации:
 * <ul>
 *   <li>/v3/api-docs - JSON спецификация OpenAPI 3</li>
 *   <li>/swagger-ui.html - интерактивный UI для тестирования API</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Value("${scada.api.base-path}")
    private String apiBasePath;

    @Bean
    public OpenAPI scadaMobileOpenAPI() {
        String description = """
                REST API для мониторинга состояния аппаратов SCADA через мобильное приложение.

                **Архитектура:**
                - Polling: backend периодически опрашивает все инстансы PrintSrv (QueryAll) и хранит snapshot-ы in-memory
                - Eventual Consistency: данные обновляются с периодом scan cycle (`printsrv.polling.fixed-delay-ms`)
                - Per-instance snapshots: каждый аппарат имеет свой набор snapshot-ов

                **Endpoints:**
                - GET %s/workshops — список цехов с актуальной статистикой
                - GET %s/workshops/{id}/units — список аппаратов цеха с текущим состоянием
                - GET %s/health/live — liveness probe
                - GET %s/health/ready — readiness probe
                """.formatted(apiBasePath, apiBasePath, apiBasePath, apiBasePath);

        return new OpenAPI()
                .info(new Info()
                        .title("SCADA Mobile Backend API")
                        .description(description)
                        .version("0.0.1-SNAPSHOT")
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development server")
                ));
    }
}
