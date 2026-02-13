package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO запроса для команды SetUnitVars.
 * <p>
 * ВАЖНО: Unit — целое число (1, 2, 3...), НЕ строка "u1"!
 * Нумерация 1-based: Unit=1 соответствует юниту "u1".
 * Можно передавать несколько параметров сразу.
 *
 * @param deviceName имя устройства
 * @param unit       номер юнита (1-based: 1="u1", 2="u2")
 * @param command    название команды (должно быть "SetUnitVars")
 * @param parameters параметры для изменения
 */
public record SetUnitVarsRequestDTO(
        @JsonProperty("DeviceName") String deviceName,
        @JsonProperty("Unit") int unit,
        @JsonProperty("Command") String command,
        @JsonProperty("Parameters") ParametersDTO parameters
) {
}
