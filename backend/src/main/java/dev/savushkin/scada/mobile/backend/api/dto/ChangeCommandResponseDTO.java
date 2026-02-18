package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * API DTO для ответа на запрос изменения команды.
 * <p>
 * Это подтверждающий ответ, указывающий, что команда была
 * добавлена в буфер и будет выполнена в следующем цикле сканирования.
 *
 * @param deviceName имя устройства
 * @param command    команда, которая была выполнена
 * @param units      карта затронутых модулей
 */
@Schema(description = "Подтверждение приёма команды SetUnitVars (acknowledgment, НЕ реальное состояние)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChangeCommandResponseDTO(
        @Schema(description = "Имя устройства", example = "Line")
        @JsonProperty("DeviceName") String deviceName,
        @Schema(description = "Название выполненной команды", example = "SetUnitVars")
        @JsonProperty("Command") String command,
        @Schema(description = "Карта затронутых units (эхо запроса, не реальное состояние из SCADA)")
        @JsonProperty("Units") Map<String, UnitStateDTO> units
) {
    public ChangeCommandResponseDTO {
        units = units == null ? Map.of() : Map.copyOf(units);
    }
}
