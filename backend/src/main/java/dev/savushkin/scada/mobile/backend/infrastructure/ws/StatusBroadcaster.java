package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveAlertStore;
import dev.savushkin.scada.mobile.backend.infrastructure.store.DowntimeTracker;
import dev.savushkin.scada.mobile.backend.services.AlertService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Рассылает live-данные через единственный WebSocket-канал {@code /ws/live}
 * после каждого scan cycle.
 * <p>
 * Поток данных:
 * <ol>
 *   <li>{@code ScanCycleScheduler} опрашивает PrintSrv, сохраняет snapshots</li>
 *   <li>Публикует {@link ScanCycleCompletedEvent}</li>
 *   <li>Этот компонент обрабатывает событие:
 *     <ul>
 *       <li>Рассылает {@code UNITS_STATUS} подписчикам активных цехов</li>
 *       <li>Вычисляет дельту алёртов и рассылает {@code ALERT} всем клиентам</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * Рассылка выполняется синхронно в потоке планировщика. Все данные in-memory,
 * поэтому задержки минимальны. При необходимости можно добавить {@code @Async}.
 */
@Component
public class StatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StatusBroadcaster.class);

    private final WorkshopService workshopService;
    private final AlertService alertService;
    private final ActiveAlertStore alertStore;
    private final DowntimeTracker downtimeTracker;
    private final LiveWsHandler liveWsHandler;

    public StatusBroadcaster(
            WorkshopService workshopService,
            AlertService alertService,
            ActiveAlertStore alertStore,
            DowntimeTracker downtimeTracker,
            LiveWsHandler liveWsHandler
    ) {
        this.workshopService = workshopService;
        this.alertService = alertService;
        this.alertStore = alertStore;
        this.downtimeTracker = downtimeTracker;
        this.liveWsHandler = liveWsHandler;
    }

    /**
     * Обрабатывает завершение scan cycle.
     * <p>
     * Порядок действий:
     * <ol>
     *   <li>Рассылает {@code UNITS_STATUS} для каждого цеха с активными подписчиками.</li>
     *   <li>Вычисляет текущие алёрты, сравнивает с предыдущим состоянием, рассылает дельты.</li>
     * </ol>
     */
    @EventListener
    public void onScanCycleCompleted(ScanCycleCompletedEvent ignoredEvent) {
        broadcastUnitsStatusForSubscribedWorkshops();
        broadcastAlertDeltas();
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void broadcastUnitsStatusForSubscribedWorkshops() {
        if (liveWsHandler.getSubscribedWorkshopIds().isEmpty()) return;

        for (String workshopId : liveWsHandler.getSubscribedWorkshopIds()) {
            try {
                var status = workshopService.getUnitsStatus(workshopId);
                var message = UnitsStatusMessageDTO.of(workshopId, status);
                liveWsHandler.broadcastToWorkshop(workshopId, liveWsHandler.toJson(message));
            } catch (JsonProcessingException e) {
                log.error("StatusBroadcaster: failed to serialize UNITS_STATUS for workshop '{}'", workshopId, e);
            }
        }
    }

    private void broadcastAlertDeltas() {
        if (liveWsHandler.getTotalSessionCount() == 0) return;

        Map<String, AlertMessageDTO> current = alertService.computeCurrentAlerts();
        ActiveAlertStore.Delta delta = alertStore.updateAndDiff(current);

        // Обновляем трекер простоев ДО проверки isEmpty:
        // onAlertStarted идемпотентен (putIfAbsent), поэтому безопасно вызывать каждый раз.
        delta.added().forEach(alert -> downtimeTracker.onAlertStarted(alert.unitId()));
        delta.removed().forEach(alert -> downtimeTracker.onAlertResolved(alert.unitId()));

        if (delta.added().isEmpty() && delta.removed().isEmpty()) return;

        String resolvedAt = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        for (AlertMessageDTO added : delta.added()) {
            sendAlert(added);
            log.info("Alert ACTIVE: unit='{}', workshop='{}', severity='{}', msg='{}'",
                    added.unitId(), added.workshopId(), added.severity(),
                    added.errors().isEmpty() ? "" : added.errors().getFirst().message());
        }

        for (AlertMessageDTO removed : delta.removed()) {
            sendAlert(removed.resolved(resolvedAt));
            log.info("Alert RESOLVED: unit='{}', workshop='{}'", removed.unitId(), removed.workshopId());
        }
    }

    private void sendAlert(AlertMessageDTO alert) {
        try {
            liveWsHandler.broadcastAlert(liveWsHandler.toJson(alert));
        } catch (JsonProcessingException e) {
            log.error("StatusBroadcaster: failed to serialize ALERT for unit '{}'", alert.unitId(), e);
        }
    }
}
