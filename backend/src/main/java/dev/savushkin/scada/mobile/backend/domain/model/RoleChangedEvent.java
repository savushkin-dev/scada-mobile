package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил роль.
 */
public record RoleChangedEvent(Long roleId, ChangeAction action) {
}
