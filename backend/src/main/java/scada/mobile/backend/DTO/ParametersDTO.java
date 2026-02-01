package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record ParametersDTO(String command) {
    @JsonCreator
    public ParametersDTO(
            @JsonProperty("command") String command
    ) {
        this.command = command;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParametersDTO that)) return false;
        return Objects.equals(command, that.command);
    }

    @Override
    public String toString() {
        return "ParametersDTO{" +
                "command='" + command + '\'' +
                '}';
    }
}
