package dev.savushkin.scada.mobile.backend.domain.model;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Set;

/**
 * Событие изменения состава устройств аппарата.
 * Публикуется когда авто-обнаружение обнаружило расхождение между БД и runtime.
 *
 * @param instanceId      идентификатор аппарата (printsrv_instance_id)
 * @param addedDevices    устройства, добавленные в БД (были в runtime, не было в БД)
 * @param removedDevices  устройства, отсутствующие в runtime (есть в БД, нет в runtime)
 * @param timestamp       время события
 */
public record DeviceCompositionChangedEvent(
        @NonNull String instanceId,
        @NonNull Set<String> addedDevices,
        @NonNull Set<String> removedDevices,
        @NonNull Instant timestamp
) {
}
