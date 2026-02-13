package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * API DTO для запроса текущего состояния системы SCADA.
 * <p>
 * Это представляет публичный контракт REST API для чтения состояния устройства.
 * Он независим от внутреннего протокола PrintSrv и может развиваться
 * отдельно для удовлетворения потребностей клиентов.
 *
 * @param deviceName имя устройства
 * @param units      карта состояний модулей по ключу модуля (например, "u1", "u2")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStateResponseDTO(
        @JsonProperty("DeviceName") String deviceName,
        @JsonProperty("Units") Map<String, UnitStateDTO> units
) {
    public QueryStateResponseDTO {
        units = units == null ? Map.of() : Map.copyOf(units);
    }
}
