package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;

/**
 * Событие изменения состояния производственного уведомления.
 * <p>
 * Публикуется {@link NotificationService} при toggle (activate / deactivate)
 * и слушается {@code StatusBroadcaster} для немедленной WS-рассылки.
 *
 * @param unitId       Идентификатор аппарата (PrintSrv instance id).
 * @param notification Новое состояние уведомления после изменения.
 * @param type         Тип изменения: {@code ACTIVATED} или {@code DEACTIVATED}.
 */
public record NotificationStateChangedEvent(
        String unitId,
        ProductionNotification notification,
        EventType type
) {
    /**
     * Тип изменения состояния уведомления.
     */
    public enum EventType {
        /** Уведомление активировано (появилось). */
        ACTIVATED,
        /** Уведомление деактивировано (снято создателем). */
        DEACTIVATED
    }
}
