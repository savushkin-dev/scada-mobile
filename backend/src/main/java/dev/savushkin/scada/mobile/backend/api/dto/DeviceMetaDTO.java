package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Per-unit мета-информация устройства для вкладки «Устройства».
 *
 * @param displayName  отображаемое имя (unit_devices.display_name ?? device_catalog.name)
 * @param showCounters показывать ли счётчики «Считано/Несчитано»
 */
public record DeviceMetaDTO(String displayName, boolean showCounters) {
}
