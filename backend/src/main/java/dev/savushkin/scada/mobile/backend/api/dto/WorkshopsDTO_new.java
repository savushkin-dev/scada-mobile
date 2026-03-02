package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * DTO для элемента списка цехов ({@code GET /api/workshops}).
 *
 * @param id           уникальный идентификатор цеха (например, {@code "dess"})
 * @param name         отображаемое название (например, {@code "Цех десертов"})
 * @param totalUnits   общее количество аппаратов/линий
 * @param problemUnits количество аппаратов с активными ошибками/предупреждениями
 */
public record WorkshopsDTO_new(
        String id,
        String name,
        int totalUnits,
        int problemUnits
) {
}
