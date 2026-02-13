package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO ответа на команду SetUnitVars.
 * Содержит ТОЛЬКО изменённые поля (частичный ответ).
 * Для получения полного состояния используйте QueryAll.
 * <p>
 * Map units защищён от изменений через Map.copyOf() — каждый ответ иммутабелен.
 *
 * @param deviceName имя устройства
 * @param command    название команды
 * @param units      карта изменённых юнитов (ключ: "u1", "u2", значение: частичное состояние юнита)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetUnitVarsResponseDTO(
        @JsonProperty("DeviceName") String deviceName,
        @JsonProperty("Command") String command,
        @JsonProperty("Units") Map<String, UnitsDTO> units
) {
    /**
     * Compact canonical constructor: создаёт защитную иммутабельную копию Map.
     * Это гарантирует thread-safety при работе с ответами команд.
     */
    public SetUnitVarsResponseDTO {
        units = units == null ? Map.of() : Map.copyOf(units);
    }
}
