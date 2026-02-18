package dev.savushkin.scada.mobile.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
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

    @Bean
    public OpenAPI scadaMobileOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SCADA Mobile Backend API")
                        .description("""
                                REST API для управления SCADA системой через мобильное приложение.

                                **Архитектура:**
                                - Write-Through Cache: POST команды добавляются в буфер, GET читает snapshot
                                - Eventual Consistency: изменения видны через 0.5-5 секунд (в зависимости от профиля)
                                - Last-Write-Wins: для одного unit применяется только последняя команда

                                **Важные особенности:**
                                - Snapshot обновляется автоматически каждые 0.5-5 секунд
                                - POST /setUnitVars возвращает подтверждение приёма (не реальное состояние)
                                - Для проверки результата используйте GET /queryAll после следующего scan cycle
                                """)
                        .version("0.0.1-SNAPSHOT")
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development server"),
                        new Server()
                                .url("http://127.0.0.1:8080")
                                .description("Local server")
                ));
    }
}

