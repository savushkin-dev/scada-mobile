package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Статическая топология устройств аппарата
 * ({@code GET /api/.../workshops/{id}/units/{unitId}/devices/topology}).
 * <p>
 * Содержит только данные, которые меняются при изменении конфигурации.
 * Предназначен для однократной загрузки и кэширования на клиенте.
 * Результат поставляется с ETag — клиент кэширует ответ и
 * обновляет его только при изменении конфига.
 *
 * @param unitId     уникальный идентификатор аппарата (instanceId)
 * @param workshopId идентификатор цеха-владельца
 * @param unit       отображаемое название аппарата/линии
 * @param devices    сгруппированный список устройств PrintSrv
 */
public record UnitDeviceTopologyDTO(
        String unitId,
        String workshopId,
        String unit,
        DeviceGroupsDTO devices
) {
}
