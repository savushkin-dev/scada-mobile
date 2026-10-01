package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Регистрирует Micrometer-метрики числа WebSocket-сессий для стенда
 * нагрузочного тестирования (профиль {@code loadtest}, эпик #51, НТ-3 #65).
 *
 * <p>Ключевая метрика для stability-теста (НТ-6): рост числа сессий при
 * постоянной нагрузке = утечка сессий.
 *
 * <ul>
 *   <li>{@code scada.ws.live.sessions} — сессии /ws/live</li>
 *   <li>{@code scada.ws.unit.sessions} — сессии /ws/unit/{id} (всего)</li>
 *   <li>{@code scada.ws.unit.active} — аппараты хотя бы с одним подписчиком</li>
 * </ul>
 */
@Component
@Profile("loadtest")
public class WsSessionMetrics {

    private final LiveWsHandler liveWsHandler;
    private final UnitWsHandler unitWsHandler;
    private final MeterRegistry meterRegistry;

    public WsSessionMetrics(LiveWsHandler liveWsHandler, UnitWsHandler unitWsHandler,
                            MeterRegistry meterRegistry) {
        this.liveWsHandler = liveWsHandler;
        this.unitWsHandler = unitWsHandler;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("scada.ws.live.sessions", liveWsHandler, LiveWsHandler::getTotalSessionCount)
                .description("Active WebSocket sessions on /ws/live")
                .register(meterRegistry);
        Gauge.builder("scada.ws.unit.sessions", unitWsHandler, UnitWsHandler::getTotalSessionCount)
                .description("Active WebSocket sessions on /ws/unit/{id}")
                .register(meterRegistry);
        Gauge.builder("scada.ws.unit.active", unitWsHandler, UnitWsHandler::getActiveUnitCount)
                .description("Units with at least one /ws/unit subscriber")
                .register(meterRegistry);
    }
}
