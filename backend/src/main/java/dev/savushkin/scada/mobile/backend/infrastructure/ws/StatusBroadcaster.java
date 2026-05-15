package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitStatusDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvInstancePolledEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveAlertStore;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveNotificationStore;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import dev.savushkin.scada.mobile.backend.services.AlertService;
import dev.savushkin.scada.mobile.backend.services.NotificationStateChangedEvent;
import dev.savushkin.scada.mobile.backend.services.UnitDetailService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *       <li>Вычисляет дельту алёрта этого аппарата и при необходимости рассылает {@code ALERT}</li>
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
    private final ActiveNotificationStore notificationStore;
    private final UnitErrorStore unitErrorStore;
    private final UnitDetailService unitDetailService;
    private final LiveWsHandler liveWsHandler;
    private final UnitWsHandler unitWsHandler;
    private final PrintSrvProperties printSrvProperties;

    public StatusBroadcaster(
            WorkshopService workshopService,
            AlertService alertService,
            ActiveAlertStore alertStore,
            ActiveNotificationStore notificationStore,
            UnitErrorStore unitErrorStore,
            UnitDetailService unitDetailService,
            LiveWsHandler liveWsHandler,
            UnitWsHandler unitWsHandler,
            PrintSrvProperties printSrvProperties
    ) {
        this.workshopService = workshopService;
        this.alertService = alertService;
        this.alertStore = alertStore;
        this.notificationStore = notificationStore;
        this.unitErrorStore = unitErrorStore;
        this.unitDetailService = unitDetailService;
        this.liveWsHandler = liveWsHandler;
        this.unitWsHandler = unitWsHandler;
        this.printSrvProperties = printSrvProperties;
    }

    @EventListener
    public void onInstancePolled(PrintSrvInstancePolledEvent event) {
        // Обновляем единый источник правды перед расчётом дельты алёртов,
        // чтобы AlertService и buildErrorsStatus читали актуальные данные.
        List<DeviceError> activeErrors = unitDetailService.extractActiveErrors(event.instanceId());
        unitErrorStore.update(event.instanceId(), activeErrors);
        broadcastUnitStatus(event.instanceId());
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
        if (liveWsHandler.getTotalSessionCount() == 0) {
            return;
        }

        AlertMessageDTO currentAlert = alertService.computeAlertForInstance(instanceId).orElse(null);
        ActiveAlertStore.Delta delta = alertStore.updateAndDiff(instanceId, currentAlert);

        if (delta.added().isEmpty() && delta.removed().isEmpty()) {
            return;
        }

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

    // ─── Notification events ─────────────────────────────────────────────────

    /**
     * Обрабатывает событие изменения состояния производственного уведомления.
     * <p>
     * Вызывается {@link dev.savushkin.scada.mobile.backend.services.NotificationService}
     * при toggle (activate / deactivate). Мгновенно рассылает {@code NOTIFICATION}
     * всем подключённым клиентам через канал {@code /ws/live}.
     *
     * @param event Событие изменения состояния.
     */
    @EventListener
    public void onNotificationChanged(NotificationStateChangedEvent event) {
        if (liveWsHandler.getTotalSessionCount() == 0) {
            return;
        }

        String unitName = printSrvProperties.getInstances().stream()
                .filter(i -> i.getId().equals(event.unitId()))
                .map(PrintSrvProperties.InstanceProperties::getDisplayName)
                .findFirst()
                .orElse(event.unitId());

        String timestamp = java.time.Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        NotificationMessageDTO dto;
        if (event.type() == NotificationStateChangedEvent.EventType.ACTIVATED) {
            dto = NotificationMessageDTO.activated(
                    event.unitId(), unitName, event.notification().creatorId(), timestamp);
        } else {
            dto = NotificationMessageDTO.deactivated(
                    event.unitId(), unitName, event.notification().creatorId(), timestamp);
        }

        ActiveNotificationStore.Delta delta = notificationStore.updateAndDiff(event.unitId(), dto);

        if (delta.added().isEmpty() && delta.removed().isEmpty()) {
            return;
        }

        for (NotificationMessageDTO added : delta.added()) {
            sendNotification(added);
            log.info("Notification ACTIVE: unit='{}', creator='{}'", added.unitId(), added.creatorId());
        }

        for (NotificationMessageDTO removed : delta.removed()) {
            sendNotification(removed);
            log.info("Notification DEACTIVATED: unit='{}', creator='{}'", removed.unitId(), removed.creatorId());
        }
    }

    private void sendNotification(NotificationMessageDTO notification) {
        liveWsHandler.broadcastNotification(notification);
    }
}
