package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Тип создателя производственного уведомления («последняя партия»).
 * <ul>
 *   <li>{@link #USER} — работник предприятия (фронтенд, пользовательский JWT);</li>
 *   <li>{@link #MACHINE} — автомат / СКАДА (machine-JWT, кнопка на самом автомате).</li>
 * </ul>
 */
public enum NotificationCreatorType {
    USER,
    MACHINE
}
