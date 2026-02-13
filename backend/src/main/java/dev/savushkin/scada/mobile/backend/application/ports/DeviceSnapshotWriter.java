package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;

/**
 * Порт записи snapshot состояния.
 * <p>
 * Нужен polling слою (scan cycle), чтобы обновлять snapshot через абстракцию,
 * позволяющую позднее заменить in-memory store на БД.
 */
public interface DeviceSnapshotWriter {

    /**
     * Сохраняет новый snapshot (заменяет предыдущий).
     */
    void save(DeviceSnapshot snapshot);
}

