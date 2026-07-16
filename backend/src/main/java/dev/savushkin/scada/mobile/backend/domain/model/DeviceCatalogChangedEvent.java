package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил справочник устройств (device_catalog).
 */
public record DeviceCatalogChangedEvent(Long catalogId, ChangeAction action) {
}
