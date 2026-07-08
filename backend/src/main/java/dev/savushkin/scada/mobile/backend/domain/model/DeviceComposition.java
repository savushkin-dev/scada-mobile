package dev.savushkin.scada.mobile.backend.domain.model;

import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

/**
 * Состав устройств аппарата — результат runtime-discovery из снапшота Line
 * или конфигурационный fallback из YAML.
 *
 * <p>Все списки неизменяемы и никогда не {@code null}:
 * пустой список означает отсутствие данной группы у конкретного аппарата.
 */
public record DeviceComposition(
        @NonNull List<String> printers,
        @NonNull List<String> aggregationCams,
        @NonNull List<String> aggregationBoxCams,
        @NonNull List<String> checkerCams
) {
    /**
     * Пустой состав — когда Line-снапшот ещё не получен.
     */
    public static DeviceComposition empty() {
        return new DeviceComposition(List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Возвращает множество всех имён устройств (всех групп).
     */
    public @NonNull Set<String> allDevices() {
        Set<String> all = new java.util.HashSet<>(printers);
        all.addAll(aggregationCams);
        all.addAll(aggregationBoxCams);
        all.addAll(checkerCams);
        return java.util.Collections.unmodifiableSet(all);
    }
}
