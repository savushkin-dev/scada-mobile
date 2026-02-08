package dev.savushkin.scada.mobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * DTO ответа на команду QueryAll.
 * Содержит полный снимок состояния всех юнитов устройства.
 * <p>
 * Map units защищён от изменений через Map.copyOf() — каждый snapshot иммутабелен.
 *
 * @param deviceName имя устройства
 * @param command    название команды
 * @param units      карта юнитов (ключ: "u1", "u2", значение: состояние юнита)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryAllResponseDTO(
        @JsonProperty("DeviceName") String deviceName,
        @JsonProperty("Command") String command,
        @JsonProperty("Units") Map<String, UnitsDTO> units
) {
    /**
     * Compact canonical constructor: создаёт защитную иммутабельную копию Map.
     * Это гарантирует thread-safety при использовании в AtomicReference.
     */
    public QueryAllResponseDTO {
        units = units == null ? Map.of() : Map.copyOf(units);
    }
}
