package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API DTO, представляющий состояние отдельного модуля SCADA.
 * <p>
 * Это публичное API представление состояния модуля,
 * независимое от формата внутреннего протокола PrintSrv.
 *
 * @param state      текущее состояние модуля
 * @param task       текущая задача модуля
 * @param counter    счётчик операций (может быть null)
 * @param properties свойства модуля
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitStateDTO(
        @JsonProperty("State") String state,
        @JsonProperty("Task") String task,
        @JsonProperty("Counter") Integer counter,
        @JsonProperty("Properties") UnitPropertiesDTO properties
) {
}
