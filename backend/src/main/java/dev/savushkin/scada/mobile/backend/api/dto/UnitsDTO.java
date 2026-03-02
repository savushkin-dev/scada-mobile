package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * DTO для элемента списка аппаратов цеха ({@code GET /api/workshops/{id}/units}).
 *
 * @param id         уникальный идентификатор аппарата (например, {@code "hassia2"})
 * @param workshopId ID цеха-владельца (для патчинга по алёртам на фронте)
 * @param unit       отображаемое название аппарата/линии
 * @param event      текущее событие или описание состояния (например, {@code "В работе"})
 * @param timer      время текущего состояния в формате {@code HH:MM:SS}
 */
public record UnitsDTO(
        String id,
        String workshopId,
        String unit,
        String event,
        String timer
) {
}
