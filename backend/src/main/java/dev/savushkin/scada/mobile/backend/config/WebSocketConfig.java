package dev.savushkin.scada.mobile.backend.config;

import dev.savushkin.scada.mobile.backend.infrastructure.ws.LiveWsHandler;
import dev.savushkin.scada.mobile.backend.infrastructure.ws.UnitWsHandler;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Конфигурация единственного WebSocket-эндпоинта {@code /ws/live}.
 * <p>
 * Единое мультиплексированное соединение заменяет три отдельных канала:
 * <ul>
 *   <li>{@code /ws/workshops/status} (упразднён — {@code problemUnits} вычисляет клиент из алёртов)</li>
 *   <li>{@code /ws/workshops/{id}/units/status} (теперь через подписку SUBSCRIBE_WORKSHOP)</li>
 *   <li>{@code /ws/alerts} (теперь встроен в /ws/live)</li>
 * </ul>
 *
 * <h3>Протокол</h3>
 * <ul>
 *   <li>При подключении сервер немедленно отправляет {@code ALERT_SNAPSHOT}.</li>
 *   <li>Клиент отправляет {@code {"action":"SUBSCRIBE_WORKSHOP","workshopId":"..."}}
 *       при входе на экран цеха.</li>
 *   <li>Клиент отправляет {@code {"action":"UNSUBSCRIBE_WORKSHOP","workshopId":"..."}}
 *       при уходе.</li>
 *   <li>Сервер пушит {@code UNITS_STATUS} подписчикам цеха и {@code ALERT} всем клиентам.</li>
 * </ul>
 * <p>
 * Политика CORS: разрешённые origins берутся из {@link CorsProperties} (application-*.yaml).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final LiveWsHandler liveWsHandler;
    private final UnitWsHandler unitWsHandler;
    private final CorsProperties corsProperties;
    private final WebSocketUserIdInterceptor userIdInterceptor;

    public WebSocketConfig(LiveWsHandler liveWsHandler, UnitWsHandler unitWsHandler,
                           CorsProperties corsProperties,
                           WebSocketUserIdInterceptor userIdInterceptor) {
        this.liveWsHandler = liveWsHandler;
        this.unitWsHandler = unitWsHandler;
        this.corsProperties = corsProperties;
        this.userIdInterceptor = userIdInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = corsProperties.getPolicy()
                .getAllowedOrigins()
                .toArray(String[]::new);
        String[] allowedOriginPatterns = corsProperties.getPolicy()
                .getAllowedOriginPatterns()
                .toArray(String[]::new);

        registry.addHandler(liveWsHandler, "/ws/live")
                .addInterceptors(userIdInterceptor)
                .setAllowedOrigins(allowedOrigins)
                .setAllowedOriginPatterns(allowedOriginPatterns);

        registry.addHandler(unitWsHandler, "/ws/unit/*")
                .addInterceptors(userIdInterceptor)
                .setAllowedOrigins(allowedOrigins)
                .setAllowedOriginPatterns(allowedOriginPatterns);

        log.info("WebSocket endpoints registered: /ws/live, /ws/unit/* (allowedOrigins: {}, allowedOriginPatterns: {})",
                corsProperties.getPolicy().getAllowedOrigins(),
                corsProperties.getPolicy().getAllowedOriginPatterns());
    }
}
