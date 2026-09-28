package dev.savushkin.scada.mobile.backend.api.dto;

import org.jspecify.annotations.Nullable;

/**
 * Per-unit мета-информация устройства для вкладки «Устройства».
 *
 * @param displayName  отображаемое имя (unit_devices.display_name ?? device_catalog.name)
 * @param showCounters показывать ли счётчики «Считано/Несчитано»
 * @param currentBatch текущая партия устройства (runtime-тег {@code curitem},
 *                     формат {@code "маркировка | партия | дата"}); {@code null},
 *                     если у устройства нет тега или снапшот недоступен
 */
public record DeviceMetaDTO(String displayName, boolean showCounters, @Nullable String currentBatch) {

    /**
     * Мета без live-данных (currentBatch = null) — для статических построителей.
     */
    public DeviceMetaDTO(String displayName, boolean showCounters) {
        this(displayName, showCounters, null);
    }
}
