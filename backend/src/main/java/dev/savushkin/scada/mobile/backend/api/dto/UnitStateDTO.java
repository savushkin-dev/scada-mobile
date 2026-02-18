package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Состояние одного unit (модуля) SCADA системы")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitStateDTO(
        @Schema(description = "Текущее состояние unit", example = "Работа")
        @JsonProperty("State") String state,
        @Schema(description = "Текущая задача unit", example = "Печать")
        @JsonProperty("Task") String task,
        @Schema(description = "Счётчик операций", example = "42")
        @JsonProperty("Counter") Integer counter,
        @Schema(description = "Свойства unit (команды, сообщения, настройки)")
        @JsonProperty("Properties") UnitPropertiesDTO properties
) {
}
