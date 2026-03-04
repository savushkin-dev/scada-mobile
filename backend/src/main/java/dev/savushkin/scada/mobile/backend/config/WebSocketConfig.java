package dev.savushkin.scada.mobile.backend.config;

import dev.savushkin.scada.mobile.backend.infrastructure.ws.UnitsStatusWsHandler;
import dev.savushkin.scada.mobile.backend.infrastructure.ws.WorkshopsStatusWsHandler;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Конфигурация WebSocket-эндпоинтов для рассылки live-статуса.
 * <p>
 * Зарегистрированные каналы:
 * <ul>
 *   <li>{@code /ws/workshops/status} — статус всех цехов (problemUnits).
 *       Сервер пушит {@link dev.savushkin.scada.mobile.backend.api.dto.WorkshopsStatusMessageDTO}
 *       после каждого scan cycle.</li>
 *   <li>{@code /ws/workshops/{workshopId}/units/status} — статус аппаратов конкретного цеха.
 *       Паттерн {@code /**} регистрируется как URI-prefix, workshopId извлекается из URI
 *       в {@link UnitsStatusWsHandler}.</li>
 * </ul>
 * <p>
 * Политика CORS: разрешённые origins берутся из {@link CorsProperties} (application-*.yaml),
 * чтобы не дублировать конфигурацию. В prod держим строгий allowlist.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final WorkshopsStatusWsHandler workshopsStatusHandler;
    private final UnitsStatusWsHandler unitsStatusHandler;
    private final CorsProperties corsProperties;

    public WebSocketConfig(
            WorkshopsStatusWsHandler workshopsStatusHandler,
            UnitsStatusWsHandler unitsStatusHandler,
            CorsProperties corsProperties
    ) {
        this.workshopsStatusHandler = workshopsStatusHandler;
        this.unitsStatusHandler = unitsStatusHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = corsProperties.getPolicy()
                .getAllowedOrigins()
                .toArray(String[]::new);

        registry.addHandler(workshopsStatusHandler, "/ws/workshops/status")
                .setAllowedOrigins(allowedOrigins);

        // Регистрируем как prefix-паттерн: обрабатывает всё вида
        // /ws/workshops/dess/units/status, /ws/workshops/cheese/units/status, …
        // workshopId извлекается из URI внутри UnitsStatusWsHandler.
        registry.addHandler(unitsStatusHandler, "/ws/workshops/*/units/status")
                .setAllowedOrigins(allowedOrigins);

        log.info("WebSocket endpoints registered: /ws/workshops/status, /ws/workshops/*/units/status" +
                " (allowedOrigins: {})", corsProperties.getPolicy().getAllowedOrigins());
    }
}
