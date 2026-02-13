package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO для юнита в ответах PrintSrv.
 * <p>
 * ВАЖНО: Поле counter может быть null потому что:
 * - QueryAll_response: содержит Counter
 * - SetUnitVars_response: НЕ содержит Counter (отсутствует в JSON)
 * <p>
 * JsonInclude(NON_NULL) гарантирует, что null поля не сериализуются в JSON.
 *
 * @param state      текущее состояние юнита
 * @param task       текущая задача юнита
 * @param counter    счётчик операций (может быть null в SetUnitVars ответе)
 * @param properties свойства юнита
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitsDTO(
        @JsonProperty("State") String state,
        @JsonProperty("Task") String task,
        @JsonProperty("Counter") Integer counter,
        @JsonProperty("Properties") PropertiesDTO properties
) {
}
