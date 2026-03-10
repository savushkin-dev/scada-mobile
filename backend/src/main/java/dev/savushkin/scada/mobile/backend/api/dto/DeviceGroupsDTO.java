package dev.savushkin.scada.mobile.backend.api.dto;

import java.util.List;

/**
 * Сгруппированный список устройств PrintSrv, отображаемых в UI.
 * <p>
 * Содержит только устройства фронтендовой зоны: принтеры и камеры.
 * Системные устройства ({@code Line}, {@code scada}, {@code BatchQueue})
 * в ответ не включаются — они не отображаются на экране
 * и используются только внутри поллинга.
 * <p>
 * Каждый список может быть пустым
 * (например, {@code printers=[]} для trepko).
 *
 * @param printers           принтеры маркировки
 * @param aggregationCams    камеры агрегации
 * @param aggregationBoxCams камеры агрегации коробки
 * @param checkerCams        камеры проверки (EAN, DataMatrix и т.д.)
 */
public record DeviceGroupsDTO(
        List<String> printers,
        List<String> aggregationCams,
        List<String> aggregationBoxCams,
        List<String> checkerCams
) {
}
