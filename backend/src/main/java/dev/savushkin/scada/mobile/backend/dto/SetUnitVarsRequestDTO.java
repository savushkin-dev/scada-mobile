package dev.savushkin.scada.mobile.backend.dto;

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
        if (!(o instanceof SetUnitVarsRequestDTO(String name, int unit1, String command1, ParametersDTO parameters1))) return false;
        return unit == unit1 && Objects.equals(deviceName, name) && Objects.equals(command, command1) && Objects.equals(parameters, parameters1);
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
