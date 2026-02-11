package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * API DTO for the response to a command change request.
 * <p>
 * This is an acknowledgment response indicating the command has been
 * buffered and will be executed in the next scan cycle.
 *
 * @param deviceName name of the device
 * @param command    command that was executed
 * @param units      map of affected units
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
