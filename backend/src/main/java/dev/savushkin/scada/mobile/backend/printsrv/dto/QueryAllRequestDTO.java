package dev.savushkin.scada.mobile.backend.printsrv.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO запроса для команды QueryAll.
 * Считывает ВСЕ теги ВСЕХ юнитов устройства.
 *
 * @param deviceName имя устройства
 * @param command    название команды (должно быть "QueryAll")
 */
public record QueryAllRequestDTO(
        @JsonProperty("DeviceName") String deviceName,
        @JsonProperty("Command") String command
) {
}
