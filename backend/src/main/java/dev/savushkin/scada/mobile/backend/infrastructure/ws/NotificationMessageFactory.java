package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import dev.savushkin.scada.mobile.backend.api.dto.NotificationMessageDTO;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationCreatorType;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import dev.savushkin.scada.mobile.backend.services.NotificationStateChangedEvent;
import dev.savushkin.scada.mobile.backend.services.UserProfileService;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Фабрика {@link NotificationMessageDTO} из доменного {@link ProductionNotification}.
 * <p>
 * Единая точка обогащения WS-сообщения: читаемое имя аппарата (топология PrintSrv)
 * и имя создателя. Для уведомлений, установленных автоматом
 * ({@link NotificationCreatorType#MACHINE}), создателем отображается «СКАДА».
 * <p>
 * Используется {@code StatusBroadcaster} (live-дельты) и
 * {@code NotificationProjectionInitializer} (прогрев WS-projection из БД при старте).
 */
@Component
public class NotificationMessageFactory {

    /** Отображаемое имя создателя для уведомлений, установленных автоматом. */
    public static final String MACHINE_CREATOR_NAME = "СКАДА";

    private final PrintSrvTopologyRepository topologyRepo;
    private final UserProfileService userProfileService;

    public NotificationMessageFactory(PrintSrvTopologyRepository topologyRepo,
                                      UserProfileService userProfileService) {
        this.topologyRepo = topologyRepo;
        this.userProfileService = userProfileService;
    }

    /**
     * Строит WS-сообщение из доменного состояния уведомления.
     *
     * @param notification доменное состояние уведомления
     * @param type         тип события (ACTIVATED / DEACTIVATED)
     * @return готовое к рассылке сообщение
     */
    public @NonNull NotificationMessageDTO fromNotification(
            @NonNull ProductionNotification notification,
            NotificationStateChangedEvent.EventType type
    ) {
        String unitName = topologyRepo.findByInstanceId(notification.unitId())
                .map(PrintSrvInstance::displayName)
                .orElse(notification.unitId());

        String creatorName = notification.creatorType() == NotificationCreatorType.MACHINE
                ? MACHINE_CREATOR_NAME
                : userProfileService.resolveFullName(notification.creatorId());

        Instant eventTime = type == NotificationStateChangedEvent.EventType.ACTIVATED
                ? notification.activatedAt()
                : notification.deactivatedAt() != null ? notification.deactivatedAt() : Instant.now();
        String timestamp = eventTime.atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        return NotificationMessageDTO.workflow(notification.unitId(), unitName,
                notification.creatorId(), creatorName, notification, timestamp);
    }
}
