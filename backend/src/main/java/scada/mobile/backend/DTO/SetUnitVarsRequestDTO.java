package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * DTO запроса для команды SetUnitVars.
 * <p>
 * ВАЖНО: Unit — целое число (1, 2, 3...), НЕ строка "u1"!
 * Нумерация 1-based: Unit=1 соответствует юниту "u1".
 * Можно передавать несколько параметров сразу.
 */
public record SetUnitVarsRequestDTO(String deviceName, int unit, String command, ParametersDTO parameters) {
    @JsonCreator
    public SetUnitVarsRequestDTO(
            @JsonProperty("DeviceName") String deviceName,
            @JsonProperty("Unit") int unit,
            @JsonProperty("Command") String command,
            @JsonProperty("Parameters") ParametersDTO parameters
    ) {
        this.deviceName = deviceName;
        this.unit = unit;
        this.command = command;
        this.parameters = parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SetUnitVarsRequestDTO that)) return false;
        return unit == that.unit && Objects.equals(deviceName, that.deviceName) && Objects.equals(command, that.command) && Objects.equals(parameters, that.parameters);
    }

    @Override
    public String toString() {
        return "SetUnitVarsRequestDTO{" +
                "deviceName='" + deviceName + '\'' +
                ", unit=" + unit +
                ", command='" + command + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
