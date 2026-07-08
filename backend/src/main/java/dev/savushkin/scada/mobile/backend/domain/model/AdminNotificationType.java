package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Типы системных уведомлений для администратора.
 */
public enum AdminNotificationType {
    /** Новое устройство обнаружено в runtime и добавлено в БД (неактивное). */
    DEVICE_DISCOVERED,

    /** Устройство есть в БД, но отсутствует в runtime. */
    DEVICE_DISCONNECTED,

    /** Ранее отключённое устройство снова появилось в runtime. */
    DEVICE_RECONNECTED
}
