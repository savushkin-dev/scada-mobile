package dev.savushkin.scada.mobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DTO ответа на команду QueryAll.
 * Содержит полный снимок состояния всех юнитов устройства.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryAllResponseDTO(String deviceName, String command, Map<String, UnitsDTO> units) {
    @JsonCreator
    public QueryAllResponseDTO(
            @JsonProperty("DeviceName") String deviceName,
            @JsonProperty("Command") String command,
            @JsonProperty("Units") Map<String, UnitsDTO> units
    ) {
        this.deviceName = deviceName;
        this.command = command;
        this.units = new HashMap<>(units);
    }

    @Override
    public Map<String, UnitsDTO> units() {
        return new HashMap<>(units);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueryAllResponseDTO(String name, String command1, Map<String, UnitsDTO> units1))) return false;
        return Objects.equals(deviceName, name) && Objects.equals(command, command1) && Objects.equals(units, units1);
    }

    @Override
    public String toString() {
        return "QueryAllResponseDTO{" +
                "deviceName='" + deviceName + '\'' +
                ", command='" + command + '\'' +
                ", units=" + units +
                '}';
    }
}
