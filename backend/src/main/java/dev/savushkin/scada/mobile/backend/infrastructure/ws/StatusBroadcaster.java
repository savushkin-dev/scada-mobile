package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.savushkin.scada.mobile.backend.api.dto.AdminNotificationMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitStatusDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceCompositionChangedEvent;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvInstancePolledEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveAlertStore;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveNotificationStore;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import dev.savushkin.scada.mobile.backend.services.AlertService;
import dev.savushkin.scada.mobile.backend.services.AdminNotificationService;
import dev.savushkin.scada.mobile.backend.services.DeviceAutoDiscoveryService;
import dev.savushkin.scada.mobile.backend.services.NotificationStateChangedEvent;
import dev.savushkin.scada.mobile.backend.services.UnitDetailService;
import dev.savushkin.scada.mobile.backend.services.UserProfileService;
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
    private final PrintSrvTopologyRepository topologyRepo;
    private final UserProfileService userProfileService;
    private final DeviceAutoDiscoveryService deviceAutoDiscoveryService;
    private final AdminNotificationService adminNotificationService;

    public StatusBroadcaster(
            WorkshopService workshopService,
            AlertService alertService,
            ActiveAlertStore alertStore,
            ActiveNotificationStore notificationStore,
            UnitErrorStore unitErrorStore,
            UnitDetailService unitDetailService,
            LiveWsHandler liveWsHandler,
            UnitWsHandler unitWsHandler,
            PrintSrvTopologyRepository topologyRepo,
            UserProfileService userProfileService,
            DeviceAutoDiscoveryService deviceAutoDiscoveryService,
            AdminNotificationService adminNotificationService
    ) {
        this.workshopService = workshopService;
        this.alertService = alertService;
        this.alertStore = alertStore;
        this.notificationStore = notificationStore;
        this.unitErrorStore = unitErrorStore;
        this.unitDetailService = unitDetailService;
        this.liveWsHandler = liveWsHandler;
        this.unitWsHandler = unitWsHandler;
        this.topologyRepo = topologyRepo;
        this.userProfileService = userProfileService;
        this.deviceAutoDiscoveryService = deviceAutoDiscoveryService;
        this.adminNotificationService = adminNotificationService;
    }

    @EventListener
    public void onInstancePolled(PrintSrvInstancePolledEvent event) {
        // Обновляем единый источник правды перед расчётом дельты алёртов,
        // чтобы AlertService и buildErrorsStatus читали актуальные данные.
        List<dev.savushkin.scada.mobile.backend.domain.model.DeviceError> activeErrors = unitDetailService.extractActiveErrors(event.instanceId());
        unitErrorStore.update(event.instanceId(), activeErrors);

        // Авто-обнаружение новых устройств из runtime
        deviceAutoDiscoveryService.syncRuntimeDevices(event.instanceId());

        broadcastUnitStatus(event.instanceId());
        broadcastAlertDelta(event.instanceId());
        broadcastUnitDetails(event.instanceId());
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private void broadcastUnitStatus(String instanceId) {
        if (liveWsHandler.getSubscribedWorkshopIds().isEmpty()) {
            return;
        }

        Long workshopId = workshopService.getWorkshopIdForInstance(instanceId).orElse(null);
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

        String unitName = topologyRepo.findByInstanceId(event.unitId())
                .map(PrintSrvInstance::displayName)
                .orElse(event.unitId());

        String timestamp = java.time.Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String creatorName = userProfileService.resolveFullName(event.notification().creatorId());

        NotificationMessageDTO dto;
        if (event.type() == NotificationStateChangedEvent.EventType.ACTIVATED) {
            dto = NotificationMessageDTO.activated(
                    event.unitId(), unitName, event.notification().creatorId(), creatorName, timestamp);
        } else {
            dto = NotificationMessageDTO.deactivated(
                    event.unitId(), unitName, event.notification().creatorId(), creatorName, timestamp);
        }

        ActiveNotificationStore.Delta delta = notificationStore.updateAndDiff(event.unitId(), dto);

        if (delta.added().isEmpty() && delta.removed().isEmpty()) {
            return;
        }

        for (NotificationMessageDTO added : delta.added()) {
            sendNotification(added);
            log.info("Notification ACTIVE: unit='{}', creator='{}' ({})",
                    added.unitId(), added.creatorId(), added.creatorName());
        }

        for (NotificationMessageDTO removed : delta.removed()) {
            sendNotification(removed);
            log.info("Notification DEACTIVATED: unit='{}', creator='{}' ({})",
                    removed.unitId(), removed.creatorId(), removed.creatorName());
        }
    }

    private void sendNotification(NotificationMessageDTO notification) {
        liveWsHandler.broadcastNotification(notification);
    }

    // ─── Admin notification events ───────────────────────────────────────────

    @EventListener
    public void onDeviceCompositionChanged(DeviceCompositionChangedEvent event) {
        for (String deviceCode : event.addedDevices()) {
            adminNotificationService.createDeviceDiscoveredNotification(event.instanceId(), deviceCode);
        }
        for (String deviceCode : event.removedDevices()) {
            adminNotificationService.createDeviceDisconnectedNotification(event.instanceId(), deviceCode);
        }
    }

    @EventListener
    public void onAdminNotification(dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationEvent event) {
        if (liveWsHandler.getTotalSessionCount() == 0) {
            return;
        }
        AdminNotificationMessageDTO dto = AdminNotificationMessageDTO.from(event.notification());
        try {
            liveWsHandler.broadcastAdminNotification(liveWsHandler.toJson(dto));
        } catch (JsonProcessingException e) {
            log.error("StatusBroadcaster: failed to serialize ADMIN_NOTIFICATION", e);
        }
    }
}
