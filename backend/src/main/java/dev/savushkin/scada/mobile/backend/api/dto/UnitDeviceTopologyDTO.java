package dev.savushkin.scada.mobile.backend.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Статическая топология устройств аппарата
 * ({@code GET /api/.../workshops/{id}/units/{unitId}/devices/topology}).
 * <p>
 * Содержит только данные, которые меняются при изменении конфигурации.
 * Предназначен для однократной загрузки и кэширования на клиенте.
 * Результат поставляется с ETag — клиент кэширует ответ и
 * обновляет его только при изменении конфига.
 *
 * @param unitId      уникальный идентификатор аппарата (instanceId)
 * @param workshopId  идентификатор цеха-владельца
 * @param unit        отображаемое название аппарата/линии
 * @param devices     сгруппированный список устройств PrintSrv (коды);
 *                    deprecated — сохранён для обратной совместимости, использовать {@code groups}
 * @param deviceNames отображаемые имена устройств из справочника (код → device_catalog.name);
 *                    deprecated — использовать {@code deviceMeta}
 * @param typeNames   отображаемые имена типов устройств (код типа → device_types.name)
 * @param groups      группы устройств для вкладки «Устройства» (группа машины первой)
 * @param deviceMeta  per-unit мета устройств: код → (displayName, showCounters)
 */
public record UnitDeviceTopologyDTO(
        String unitId,
        long workshopId,
        String unit,
        DeviceGroupsDTO devices,
        Map<String, String> deviceNames,
        Map<String, String> typeNames,
        List<DeviceGroupDTO> groups,
        Map<String, DeviceMetaDTO> deviceMeta
) {
}
