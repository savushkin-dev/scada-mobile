package dev.savushkin.scada.mobile.backend.domain.model;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.AdminNotificationEntity;
import org.jspecify.annotations.NonNull;

/**
 * Событие создания системного уведомления для администратора.
 * Публикуется {@link AdminNotificationService} и обрабатывается
 * {@link dev.savushkin.scada.mobile.backend.infrastructure.ws.StatusBroadcaster}
 * для рассылки через WebSocket.
 *
 * @param notification созданное уведомление
 */
public record AdminNotificationEvent(
        @NonNull AdminNotificationEntity notification
) {
}
