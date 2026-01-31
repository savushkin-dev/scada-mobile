package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class QueryAllResponseDTO {
    private final String deviceName;
    private final String command;
    private final Map<String, UnitsDTO> units;

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

    public String getDeviceName() {
        return deviceName;
    }

    public String getCommand() {
        return command;
    }

    public Map<String, UnitsDTO> getUnits() {
        return new HashMap<>(units);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueryAllResponseDTO that)) return false;
        return Objects.equals(deviceName, that.deviceName) && Objects.equals(command, that.command) && Objects.equals(units, that.units);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceName, command, units);
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
