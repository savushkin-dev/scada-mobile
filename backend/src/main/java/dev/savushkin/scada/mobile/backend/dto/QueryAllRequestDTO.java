package dev.savushkin.scada.mobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * DTO запроса для команды QueryAll.
 * Считывает ВСЕ теги ВСЕХ юнитов устройства.
 */
public record QueryAllRequestDTO(String deviceName, String command) {
    @JsonCreator
    public QueryAllRequestDTO(
            @JsonProperty("DeviceName") String deviceName,
            @JsonProperty("Command") String command
    ) {
        this.deviceName = deviceName;
        this.command = command;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueryAllRequestDTO(String name, String command1))) return false;
        return Objects.equals(deviceName, name) && Objects.equals(command, command1);
    }

    @Override
    public String toString() {
        return "QueryAllRequestDTO{" +
                "deviceName='" + deviceName + '\'' +
                ", command='" + command + '\'' +
                '}';
    }
}
