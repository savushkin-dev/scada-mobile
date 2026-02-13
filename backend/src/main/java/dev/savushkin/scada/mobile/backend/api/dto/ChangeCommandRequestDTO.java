package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API DTO для запроса изменения команды на модуле.
 * <p>
 * Это представляет публичный контракт REST API для операций записи.
 *
 * @param unit  номер модуля (индексация с 1)
 * @param value значение команды для установки
 */
public record ChangeCommandRequestDTO(
        @JsonProperty("unit") int unit,
        @JsonProperty("value") int value
) {
}
