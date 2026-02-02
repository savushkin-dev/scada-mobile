package dev.savushkin.scada.mobile.backend.dto;

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
        if (!(o instanceof ParametersDTO(String command1))) return false;
        return Objects.equals(command, command1);
    }

    @Override
    public String toString() {
        return "ParametersDTO{" +
                "command='" + command + '\'' +
                '}';
    }
}
