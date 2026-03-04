package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Рассылает live-статус по WebSocket-каналам после каждого scan cycle.
 * <p>
 * Поток данных:
 * <ol>
 *   <li>{@code ScanCycleScheduler} опрашивает PrintSrv и сохраняет snapshots</li>
 *   <li>Публикует {@link ScanCycleCompletedEvent}</li>
 *   <li>Этот компонент обрабатывает событие и вычисляет актуальный статус</li>
 *   <li>Рассылает результат через {@link WorkshopsStatusWsHandler} и {@link UnitsStatusWsHandler}</li>
 * </ol>
 * <p>
 * Рассылка выполняется в потоке планировщика (synchronously). Поскольку
 * все данные in-memory, а Spring WebSocket ({@code sendMessage}) для
 * server-side sessions не блокирует надолго — это допустимо для текущего масштаба.
 * При необходимости можно добавить {@code @Async}.
 */
@Component
public class StatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StatusBroadcaster.class);

    private final WorkshopService workshopService;
    private final WorkshopsStatusWsHandler workshopsHandler;
    private final UnitsStatusWsHandler unitsHandler;
    private final ObjectMapper objectMapper;

    public StatusBroadcaster(
            WorkshopService workshopService,
            WorkshopsStatusWsHandler workshopsHandler,
            UnitsStatusWsHandler unitsHandler,
            ObjectMapper objectMapper
    ) {
        this.workshopService = workshopService;
        this.workshopsHandler = workshopsHandler;
        this.unitsHandler = unitsHandler;
        this.objectMapper = objectMapper;
    }

    /**
     * Обрабатывает событие завершения scan cycle.
     * <p>
     * Всегда рассылает статус цехов (канал shallow — только счётчики).
     * Статус аппаратов рассылается только тем цехам, у которых есть активные WS-клиенты.
     */
    @EventListener
    public void onScanCycleCompleted(ScanCycleCompletedEvent ignoredEvent) {
        broadcastWorkshopsStatus();
        broadcastUnitsStatusForActiveWorkshops();
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void broadcastWorkshopsStatus() {
        if (workshopsHandler.getSessionCount() == 0) return;
        try {
            var message = WorkshopsStatusMessageDTO.of(workshopService.getWorkshopsStatus());
            workshopsHandler.broadcast(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.error("StatusBroadcaster: failed to serialize workshops status", e);
        }
    }

    private void broadcastUnitsStatusForActiveWorkshops() {
        if (unitsHandler.getActiveWorkshopCount() == 0) return;
        for (String workshopId : unitsHandler.getActiveWorkshopIds()) {
            try {
                var status = workshopService.getUnitsStatus(workshopId);
                var message = UnitsStatusMessageDTO.of(workshopId, status);
                unitsHandler.broadcastToWorkshop(workshopId, objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException e) {
                log.error("StatusBroadcaster: failed to serialize units status for workshop '{}'", workshopId, e);
            }
        }
    }
}
