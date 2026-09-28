package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationRepository;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveNotificationStore;
import dev.savushkin.scada.mobile.backend.services.NotificationStateChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Прогрев WS-projection ({@link ActiveNotificationStore}) из перманентного хранилища
 * при старте приложения.
 * <p>
 * Состояние «последняя партия» переживает рестарт backend (PostgreSQL), поэтому
 * сразу после готовности приложения активные уведомления загружаются в
 * WS-projection — иначе {@code NOTIFICATION_SNAPSHOT} для новых WS-клиентов
 * расходился бы с единым источником истины до первого toggle-события.
 */
@Component
public class NotificationProjectionInitializer {

    private static final Logger log = LoggerFactory.getLogger(NotificationProjectionInitializer.class);

    private final NotificationRepository notificationRepository;
    private final ActiveNotificationStore notificationStore;
    private final NotificationMessageFactory messageFactory;

    public NotificationProjectionInitializer(NotificationRepository notificationRepository,
                                             ActiveNotificationStore notificationStore,
                                             NotificationMessageFactory messageFactory) {
        this.notificationRepository = notificationRepository;
        this.notificationStore = notificationStore;
        this.messageFactory = messageFactory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        List<ProductionNotification> active = notificationRepository.findAllActive();
        for (ProductionNotification notification : active) {
            notificationStore.updateAndDiff(
                    notification.unitId(),
                    messageFactory.fromNotification(notification, NotificationStateChangedEvent.EventType.ACTIVATED));
        }
        log.info("Notification projection warmed up: {} active notification(s) loaded from DB", active.size());
    }
}
