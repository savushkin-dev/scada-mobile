package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API DTO for requesting a command change on a unit.
 * <p>
 * This represents the public REST API contract for write operations.
 *
 * @param unit  unit number (1-based)
 * @param value command value to set
 */
public record ChangeCommandRequestDTO(
        @JsonProperty("unit") int unit,
        @JsonProperty("value") int value
) {
}
