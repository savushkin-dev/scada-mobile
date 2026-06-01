package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Live-статус аппарата — рассылается по WebSocket {@code /ws/workshops/{id}/units/status}.
 * <p>
 * Содержит только динамическую часть: текущее событие аппарата.
 *
 * @param unitId        идентификатор аппарата (ссылка на {@link UnitTopologyDTO#id()})
 * @param workshopId    идентификатор цеха-владельца
 * @param event         текущее событие (curItem без ошибок или список ошибок устройств)
 * @param cameraRead    считано марок камерой проверки (Total), или null
 * @param cameraUnread  несчитано марок камерой проверки (Failed), или null
 */
public record UnitStatusDTO(
        String unitId,
        long workshopId,
        String event,
        String cameraRead,
        String cameraUnread
) {
}
