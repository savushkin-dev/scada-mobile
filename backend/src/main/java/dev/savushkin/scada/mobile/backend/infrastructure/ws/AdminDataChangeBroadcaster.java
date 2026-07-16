package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.savushkin.scada.mobile.backend.api.dto.*;
import dev.savushkin.scada.mobile.backend.domain.model.*;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter.PrintSrvTopologyJpaAdapter;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.*;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.*;
import dev.savushkin.scada.mobile.backend.services.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Слушает доменные события об изменениях админ-данных и рассылает
 * актуальные данные (перечитанные из БД после коммита) по WebSocket.
 * <p>
 * БД остаётся единым источником правды: события обрабатываются
 * в фазе {@code AFTER_COMMIT}, после чего затронутая сущность
 * загружается заново и только потом сериализуется в JSON.
 */
@Component
public class AdminDataChangeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(AdminDataChangeBroadcaster.class);

    private final LiveWsHandler liveWsHandler;
    private final AuthService authService;
    private final PrintSrvTopologyJpaAdapter topologyAdapter;

    private final UserJpaRepository userRepository;
    private final WorkshopJpaRepository workshopRepository;
    private final RoleJpaRepository roleRepository;
    private final UnitJpaRepository unitRepository;
    private final DeviceJpaRepository deviceRepository;
    private final DeviceCatalogJpaRepository catalogRepository;
    private final DeviceTypeJpaRepository deviceTypeRepository;
    private final UserNotificationSettingsJpaRepository settingsRepository;

    public AdminDataChangeBroadcaster(
            LiveWsHandler liveWsHandler,
            AuthService authService,
            PrintSrvTopologyJpaAdapter topologyAdapter,
            UserJpaRepository userRepository,
            WorkshopJpaRepository workshopRepository,
            RoleJpaRepository roleRepository,
            UnitJpaRepository unitRepository,
            DeviceJpaRepository deviceRepository,
            DeviceCatalogJpaRepository catalogRepository,
            DeviceTypeJpaRepository deviceTypeRepository,
            UserNotificationSettingsJpaRepository settingsRepository
    ) {
        this.liveWsHandler = liveWsHandler;
        this.authService = authService;
        this.topologyAdapter = topologyAdapter;
        this.userRepository = userRepository;
        this.workshopRepository = workshopRepository;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.deviceRepository = deviceRepository;
        this.catalogRepository = catalogRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.settingsRepository = settingsRepository;
    }

    // ─── Employees ───────────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmployeeChanged(EmployeeChangedEvent event) {
        if (event.action() == ChangeAction.DELETE) {
            sendToAdminsAndUser(event.employeeId(), EmployeeChangedMessageDTO.of(
                    new EmployeeChangedMessageDTO.EmployeePayload(event.employeeId(), null, null, null, false),
                    event.action().name()
            ));
            sendForceLogout(event.employeeId(), "User deleted");
            return;
        }

        userRepository.findById(event.employeeId()).ifPresentOrElse(
                user -> {
                    var payload = new EmployeeChangedMessageDTO.EmployeePayload(
                            user.getId(),
                            user.getFullName(),
                            user.getCode(),
                            user.getRoleId(),
                            user.isActive()
                    );
                    sendToAdminsAndUser(user.getId(), EmployeeChangedMessageDTO.of(payload, event.action().name()));

                    if (!user.isActive()) {
                        authService.revokeAllRefreshTokens(user.getId());
                        sendForceLogout(user.getId(), "User deactivated");
                    }
                },
                () -> log.warn("AdminDataChangeBroadcaster: employee {} not found after commit", event.employeeId())
        );
    }

    // ─── Workshops ───────────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorkshopChanged(WorkshopChangedEvent event) {
        topologyAdapter.invalidateETag();

        if (event.action() == ChangeAction.DELETE) {
            broadcast(WorkshopChangedMessageDTO.of(
                    new WorkshopChangedMessageDTO.WorkshopPayload(event.workshopId(), null, false, 0),
                    event.action().name()
            ));
            return;
        }

        workshopRepository.findById(event.workshopId()).ifPresentOrElse(
                workshop -> {
                    int totalUnits = (int) unitRepository.countByWorkshop_Id(workshop.getId());
                    var payload = new WorkshopChangedMessageDTO.WorkshopPayload(
                            workshop.getId(),
                            workshop.getName(),
                            workshop.isActive(),
                            totalUnits
                    );
                    broadcast(WorkshopChangedMessageDTO.of(payload, event.action().name()));
                },
                () -> log.warn("AdminDataChangeBroadcaster: workshop {} not found after commit", event.workshopId())
        );
    }

    // ─── Roles ───────────────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoleChanged(RoleChangedEvent event) {
        if (event.action() == ChangeAction.DELETE) {
            sendToAdmins(RoleChangedMessageDTO.of(
                    new RoleChangedMessageDTO.RolePayload(event.roleId(), null),
                    event.action().name()
            ));
            return;
        }

        roleRepository.findById(event.roleId()).ifPresentOrElse(
                role -> {
                    var payload = new RoleChangedMessageDTO.RolePayload(role.getId(), role.getName());
                    sendToAdmins(RoleChangedMessageDTO.of(payload, event.action().name()));
                },
                () -> log.warn("AdminDataChangeBroadcaster: role {} not found after commit", event.roleId())
        );
    }

    // ─── Units ─────────────────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUnitChanged(UnitChangedEvent event) {
        topologyAdapter.invalidateETag();

        if (event.action() == ChangeAction.DELETE) {
            broadcast(UnitChangedMessageDTO.of(
                    new UnitChangedMessageDTO.UnitPayload(
                            event.unitId(),
                            event.printsrvInstanceId(),
                            event.workshopId(),
                            null,
                            false
                    ),
                    event.action().name()
            ));
            return;
        }

        unitRepository.findById(event.unitId()).ifPresentOrElse(
                unit -> {
                    var payload = new UnitChangedMessageDTO.UnitPayload(
                            unit.getId(),
                            unit.getPrintsrvInstanceId(),
                            unit.getWorkshopId(),
                            unit.getName(),
                            unit.isActive()
                    );
                    broadcast(UnitChangedMessageDTO.of(payload, event.action().name()));
                },
                () -> log.warn("AdminDataChangeBroadcaster: unit {} not found after commit", event.unitId())
        );
    }

    // ─── Devices (unit_devices) ──────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeviceChanged(DeviceChangedEvent event) {
        topologyAdapter.invalidateETag();

        if (event.action() == ChangeAction.DELETE) {
            broadcast(DeviceChangedMessageDTO.of(
                    new DeviceChangedMessageDTO.DevicePayload(
                            event.deviceId(),
                            event.unitId(),
                            event.printsrvInstanceId(),
                            null
                    ),
                    event.action().name()
            ));
            return;
        }

        deviceRepository.findById(event.deviceId()).ifPresentOrElse(
                device -> {
                    UnitEntity unit = device.getUnit();
                    var payload = new DeviceChangedMessageDTO.DevicePayload(
                            device.getId(),
                            unit != null ? unit.getId() : null,
                            unit != null ? unit.getPrintsrvInstanceId() : null,
                            device.getCatalogId()
                    );
                    broadcast(DeviceChangedMessageDTO.of(payload, event.action().name()));
                },
                () -> log.warn("AdminDataChangeBroadcaster: device {} not found after commit", event.deviceId())
        );
    }

    // ─── Device catalog ──────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeviceCatalogChanged(DeviceCatalogChangedEvent event) {
        topologyAdapter.invalidateETag();

        if (event.action() == ChangeAction.DELETE) {
            broadcast(DeviceCatalogChangedMessageDTO.of(
                    new DeviceCatalogChangedMessageDTO.DeviceCatalogPayload(
                            event.catalogId(), null, null, null, false
                    ),
                    event.action().name()
            ));
            return;
        }

        catalogRepository.findById(event.catalogId()).ifPresentOrElse(
                catalog -> {
                    var payload = new DeviceCatalogChangedMessageDTO.DeviceCatalogPayload(
                            catalog.getId(),
                            catalog.getCode(),
                            catalog.getName(),
                            catalog.getTypeId(),
                            catalog.isActive()
                    );
                    broadcast(DeviceCatalogChangedMessageDTO.of(payload, event.action().name()));
                },
                () -> log.warn("AdminDataChangeBroadcaster: device catalog {} not found after commit", event.catalogId())
        );
    }

    // ─── Device types ────────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeviceTypeChanged(DeviceTypeChangedEvent event) {
        topologyAdapter.invalidateETag();

        if (event.action() == ChangeAction.DELETE) {
            broadcast(DeviceTypeChangedMessageDTO.of(
                    new DeviceTypeChangedMessageDTO.DeviceTypePayload(event.typeId(), null, null),
                    event.action().name()
            ));
            return;
        }

        deviceTypeRepository.findById(event.typeId()).ifPresentOrElse(
                type -> {
                    var payload = new DeviceTypeChangedMessageDTO.DeviceTypePayload(
                            type.getId(),
                            type.getCode(),
                            type.getName()
                    );
                    broadcast(DeviceTypeChangedMessageDTO.of(payload, event.action().name()));
                },
                () -> log.warn("AdminDataChangeBroadcaster: device type {} not found after commit", event.typeId())
        );
    }

    // ─── User notification settings ──────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserNotificationSettingsChanged(UserNotificationSettingsChangedEvent event) {
        if (event.action() == ChangeAction.DELETE) {
            sendToAdminsAndUser(event.userId(), UserNotificationSettingsChangedMessageDTO.of(
                    new UserNotificationSettingsChangedMessageDTO.UserNotificationSettingsPayload(
                            event.settingId(), event.userId(), null, false, false, false
                    ),
                    event.action().name()
            ));
            return;
        }

        settingsRepository.findById(event.settingId()).ifPresentOrElse(
                settings -> {
                    var payload = new UserNotificationSettingsChangedMessageDTO.UserNotificationSettingsPayload(
                            settings.getId(),
                            settings.getUserId(),
                            settings.getUnitId(),
                            settings.isIncidentNotificationsEnabled(),
                            settings.isAndroidCallNotificationsEnabled(),
                            settings.isActive()
                    );
                    sendToAdminsAndUser(settings.getUserId(), UserNotificationSettingsChangedMessageDTO.of(payload, event.action().name()));
                },
                () -> log.warn("AdminDataChangeBroadcaster: notification settings {} not found after commit", event.settingId())
        );
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void sendToAdminsAndUser(long userId, Object message) {
        sendToAdmins(message);
        sendToUser(userId, message);
    }

    private void sendToAdmins(Object message) {
        try {
            liveWsHandler.sendToAdmins(liveWsHandler.toJson(message));
        } catch (JsonProcessingException e) {
            log.error("AdminDataChangeBroadcaster: failed to serialize message for admins", e);
        }
    }

    private void sendToUser(long userId, Object message) {
        try {
            liveWsHandler.sendToUser(userId, liveWsHandler.toJson(message));
        } catch (JsonProcessingException e) {
            log.error("AdminDataChangeBroadcaster: failed to serialize message for user {}", userId, e);
        }
    }

    private void broadcast(Object message) {
        try {
            liveWsHandler.broadcastToAll(liveWsHandler.toJson(message));
        } catch (JsonProcessingException e) {
            log.error("AdminDataChangeBroadcaster: failed to serialize broadcast message", e);
        }
    }

    private void sendForceLogout(long userId, String reason) {
        try {
            liveWsHandler.sendToUser(userId, liveWsHandler.toJson(ForceLogoutMessageDTO.of(reason)));
            log.info("AdminDataChangeBroadcaster: sent FORCE_LOGOUT to userId={}", userId);
        } catch (JsonProcessingException e) {
            log.error("AdminDataChangeBroadcaster: failed to serialize FORCE_LOGOUT for user {}", userId, e);
        }
    }
}
