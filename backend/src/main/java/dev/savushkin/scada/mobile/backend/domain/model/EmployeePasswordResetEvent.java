package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор сбросил пароль сотрудника.
 * <p>
 * Публикуется вместе с отзывом всех refresh-токенов, чтобы слушатель
 * мог немедленно разлогинить активные WebSocket-сессии сотрудника
 * (FORCE_LOGOUT), не дожидаясь истечения access-токена.
 */
public record EmployeePasswordResetEvent(Long userId) {
}
