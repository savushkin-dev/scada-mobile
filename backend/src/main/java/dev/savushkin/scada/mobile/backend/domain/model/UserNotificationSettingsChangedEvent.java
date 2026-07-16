package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил настройки уведомлений пользователя.
 */
public record UserNotificationSettingsChangedEvent(Long settingId, Long userId, ChangeAction action) {
}
