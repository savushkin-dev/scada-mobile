package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Live-статус аппарата — рассылается по WebSocket {@code /ws/workshops/{id}/units/status}.
 * <p>
 * Содержит только динамическую часть: текущее событие и таймер состояния.
 *
 * @param unitId     идентификатор аппарата (ссылка на {@link UnitTopologyDTO#id()})
 * @param workshopId идентификатор цеха-владельца
 * @param event      текущее событие ({@code "В работе"}, {@code "Остановлена"}, {@code "Ошибка…"})
 * @param timer      время текущего состояния в формате {@code HH:MM:SS}
 */
public record UnitStatusDTO(
        String unitId,
        String workshopId,
        String event,
        String timer
) {
}
