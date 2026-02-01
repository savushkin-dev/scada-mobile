package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DTO ответа на команду SetUnitVars.
 * Содержит ТОЛЬКО изменённые поля (частичный ответ).
 * Для получения полного состояния используйте QueryAll.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetUnitVarsResponseDTO(String deviceName, String command, Map<String, UnitsDTO> units) {
    @JsonCreator
    public SetUnitVarsResponseDTO(
            @JsonProperty("DeviceName") String deviceName,
            @JsonProperty("Command") String command,
            @JsonProperty("Units") Map<String, UnitsDTO> units) {
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
        if (!(o instanceof SetUnitVarsResponseDTO that)) return false;
        return Objects.equals(deviceName, that.deviceName) && Objects.equals(command, that.command) && Objects.equals(units, that.units);
    }

    @Override
    public String toString() {
        return "SetUnitVarsResponseDTO{" +
                "deviceName='" + deviceName + '\'' +
                ", command='" + command + '\'' +
                ", units=" + units +
                '}';
    }
}
