package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class SetUnitVarsDTO {
    private final String deviceName;
    private final int unit;
    private final String command;
    private final ParametersDTO parameters;

    @JsonCreator
    public SetUnitVarsDTO(
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

    public String getDeviceName() {
        return deviceName;
    }

    public int getUnit() {
        return unit;
    }

    public String getCommand() {
        return command;
    }

    public ParametersDTO getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SetUnitVarsDTO that)) return false;
        return unit == that.unit && Objects.equals(deviceName, that.deviceName) && Objects.equals(command, that.command) && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceName, unit, command, parameters);
    }

    @Override
    public String toString() {
        return "SetUnitVarsDTO{" +
                "deviceName='" + deviceName + '\'' +
                ", unit=" + unit +
                ", command='" + command + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
