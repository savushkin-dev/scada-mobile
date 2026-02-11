package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * API DTO for querying the current state of the SCADA system.
 * <p>
 * This represents the public REST API contract for reading device state.
 * It is independent of the internal PrintSrv protocol and can evolve
 * separately to meet client needs.
 *
 * @param deviceName name of the device
 * @param units      map of unit states by unit key (e.g., "u1", "u2")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStateResponseDTO(
        @JsonProperty("DeviceName") String deviceName,
        @JsonProperty("Units") Map<String, UnitStateDTO> units
) {
    public QueryStateResponseDTO {
        units = units == null ? Map.of() : Map.copyOf(units);
    }
}
