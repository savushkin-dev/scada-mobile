package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Live-статус аппарата — рассылается по WebSocket {@code /ws/workshops/{id}/units/status}.
 * <p>
 * Содержит только динамическую часть: текущее событие аппарата.
 *
 * @param unitId     идентификатор аппарата (ссылка на {@link UnitTopologyDTO#id()})
 * @param workshopId идентификатор цеха-владельца
 * @param event      текущее событие (curItem без ошибок или список ошибок устройств)
 */
public record UnitStatusDTO(
        String unitId,
        String workshopId,
        String event
) {
}
