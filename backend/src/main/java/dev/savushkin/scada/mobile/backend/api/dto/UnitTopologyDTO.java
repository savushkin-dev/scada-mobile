package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Статическая топология аппарата ({@code GET /api/v1.0.0/workshops/{id}/units/topology}).
 * <p>
 * Содержит только данные, которые меняются при изменении конфигурации.
 * Предназначен для однократной загрузки и кэширования на клиенте.
 *
 * @param id         уникальный идентификатор аппарата
 * @param workshopId ID цеха-владельца
 * @param unit       отображаемое название аппарата/линии
 */
public record UnitTopologyDTO(
        String id,
        String workshopId,
        String unit
) {
}
