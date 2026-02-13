package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChangeCommandResponseDTO(
        @JsonProperty("DeviceName") String deviceName,
        @JsonProperty("Command") String command,
        @JsonProperty("Units") Map<String, UnitStateDTO> units
) {
    public ChangeCommandResponseDTO {
        units = units == null ? Map.of() : Map.copyOf(units);
    }
}
