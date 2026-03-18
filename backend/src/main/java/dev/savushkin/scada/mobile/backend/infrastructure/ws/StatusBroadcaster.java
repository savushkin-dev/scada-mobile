package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitStatusDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.infrastructure.notification.AlertNotificationEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvInstancePolledEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveAlertStore;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import dev.savushkin.scada.mobile.backend.services.AlertService;
import dev.savushkin.scada.mobile.backend.services.UnitDetailService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Рассылает live-данные через единственный WebSocket-канал {@code /ws/live}
 * по мере готовности конкретных инстансов PrintSrv.
 * <p>
 * Поток данных:
 * <ol>
 *   <li>Worker инстанса опрашивает PrintSrv и сохраняет snapshots</li>
 *   <li>Публикует {@link PrintSrvInstancePolledEvent}</li>
 *   <li>Этот компонент обрабатывает событие:
 *     <ul>
 *       <li>Рассылает обновление статуса конкретного аппарата</li>
 *       <li>Вычисляет дельту алёрта этого аппарата; при изменении дельты рассылает
 *           {@code ALERT} через WebSocket <em>и</em> публикует
 *           {@link AlertNotificationEvent} для delivery-адаптеров (Web Push и др.)</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Component
public class StatusBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StatusBroadcaster.class);

    private final WorkshopService workshopService;
    private final AlertService alertService;
    private final ActiveAlertStore alertStore;
    private final UnitErrorStore unitErrorStore;
    private final UnitDetailService unitDetailService;
    private final LiveWsHandler liveWsHandler;
    private final UnitWsHandler unitWsHandler;
    private final ApplicationEventPublisher eventPublisher;

    public StatusBroadcaster(
            WorkshopService workshopService,
            AlertService alertService,
            ActiveAlertStore alertStore,
            UnitErrorStore unitErrorStore,
            UnitDetailService unitDetailService,
            LiveWsHandler liveWsHandler,
            UnitWsHandler unitWsHandler,
            ApplicationEventPublisher eventPublisher
    ) {
        this.workshopService = workshopService;
        this.alertService = alertService;
        this.alertStore = alertStore;
        this.unitErrorStore = unitErrorStore;
        this.unitDetailService = unitDetailService;
        this.liveWsHandler = liveWsHandler;
        this.unitWsHandler = unitWsHandler;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onInstancePolled(PrintSrvInstancePolledEvent event) {
        broadcastUnitStatus(event.instanceId());
        // Обновляем единый источник правды перед расчётом дельты алёртов,
        // чтобы AlertService и buildErrorsStatus читали актуальные данные.
        List<DeviceError> activeErrors = unitDetailService.extractActiveErrors(event.instanceId());
        unitErrorStore.update(event.instanceId(), activeErrors);
        broadcastAlertDelta(event.instanceId());
        broadcastUnitDetails(event.instanceId());
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void broadcastUnitStatus(String instanceId) {
        if (liveWsHandler.getSubscribedWorkshopIds().isEmpty()) {
            return;
        }

        String workshopId = workshopService.getWorkshopIdForInstance(instanceId).orElse(null);
        if (workshopId == null || !liveWsHandler.getSubscribedWorkshopIds().contains(workshopId)) {
            return;
        }

        UnitStatusDTO status = workshopService.getUnitStatus(instanceId).orElse(null);
        if (status == null) {
            return;
        }

        try {
            UnitsStatusMessageDTO message = UnitsStatusMessageDTO.of(workshopId, List.of(status));
            liveWsHandler.broadcastToWorkshop(workshopId, liveWsHandler.toJson(message));
        } catch (JsonProcessingException e) {
            log.error("StatusBroadcaster: failed to serialize UNITS_STATUS for instance '{}'", instanceId, e);
        }
    }

    private void broadcastAlertDelta(String instanceId) {
        AlertMessageDTO currentAlert = alertService.computeAlertForInstance(instanceId).orElse(null);
        ActiveAlertStore.Delta delta = alertStore.updateAndDiff(instanceId, currentAlert);

        if (delta.added().isEmpty() && delta.removed().isEmpty()) {
            return;
        }

        String resolvedAt = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        for (AlertMessageDTO added : delta.added()) {
            if (liveWsHandler.getTotalSessionCount() > 0) {
                sendAlert(added);
            }
            String errorSignature = ActiveAlertStore.computeErrorSignature(added.errors());
            eventPublisher.publishEvent(
                    AlertNotificationEvent.activated(this,
                            added.workshopId(), added.unitId(), added.unitName(),
                            added.severity(), added.timestamp(), errorSignature, added.errors())
            );
            log.info("Alert ACTIVE: unit='{}', workshop='{}', severity='{}', signature='{}', msg='{}'",
                    added.unitId(), added.workshopId(), added.severity(), errorSignature,
                    added.errors().isEmpty() ? "" : added.errors().getFirst().message());
        }

        for (AlertMessageDTO removed : delta.removed()) {
            AlertMessageDTO resolvedAlert = removed.resolved(resolvedAt);
            if (liveWsHandler.getTotalSessionCount() > 0) {
                sendAlert(resolvedAlert);
            }
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

    /**
     * Рассылает обновления по всем четырём типам сообщений
     * подписчикам канала {@code /ws/unit/{instanceId}}.
     * <p>
     * Вызов является no-op если нет активных подписчиков на данный аппарат.
     */
    private void broadcastUnitDetails(String instanceId) {
        if (unitWsHandler.getSubscriberCount(instanceId) == 0) {
            return;
        }
        unitWsHandler.broadcastToUnit(instanceId);
    }
}
