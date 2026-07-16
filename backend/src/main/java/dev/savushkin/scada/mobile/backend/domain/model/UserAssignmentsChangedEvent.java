package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Событие изменения закрепления аппаратов за пользователем.
 * <p>
 * Публикуется после изменения назначений (CRUD пользователя, CRUD назначений)
 * и обрабатывается WebSocket-рассыльщиком для мгновенного обновления
 * списка закреплённых аппаратов у самого сотрудника.
 */
public record UserAssignmentsChangedEvent(Long userId) {
}
