package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class QueryAllRequestDTO {
    private final String deviceName;
    private final String command;

    @JsonCreator
    public QueryAllRequestDTO(
            @JsonProperty("DeviceName") String deviceName,
            @JsonProperty("Command") String command
    ) {
        this.deviceName = deviceName;
        this.command = command;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getCommand() {
        return command;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueryAllRequestDTO that)) return false;
        return Objects.equals(deviceName, that.deviceName) && Objects.equals(command, that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceName, command);
    }

    @Override
    public String toString() {
        return "QueryAllRequestDTO{" +
                "deviceName='" + deviceName + '\'' +
                ", command='" + command + '\'' +
                '}';
    }
}
