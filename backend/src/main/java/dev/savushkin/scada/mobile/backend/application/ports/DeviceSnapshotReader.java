package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;

/**
 * Порт чтения текущего snapshot состояния.
 * <p>
 * Application слой зависит от этого интерфейса, а конкретная реализация (in-memory/DB/...) находится в infrastructure.
 */
public interface DeviceSnapshotReader {

    /**
     * @return последний snapshot или null, если он ещё ни разу не был получен.
     */
    DeviceSnapshot getLatestOrNull();
}

