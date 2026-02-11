package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API DTO representing the state of a single SCADA unit.
 * <p>
 * This is the public API representation of a unit's state,
 * independent of the internal PrintSrv protocol format.
 *
 * @param state      current state of the unit
 * @param task       current task of the unit
 * @param counter    operation counter (may be null)
 * @param properties unit properties
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitStateDTO(
        @JsonProperty("State") String state,
        @JsonProperty("Task") String task,
        @JsonProperty("Counter") Integer counter,
        @JsonProperty("Properties") UnitPropertiesDTO properties
) {
}
