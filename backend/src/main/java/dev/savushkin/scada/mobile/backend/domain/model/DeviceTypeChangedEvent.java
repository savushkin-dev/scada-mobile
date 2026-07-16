package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил тип устройства.
 */
public record DeviceTypeChangedEvent(Long typeId, ChangeAction action) {
}
