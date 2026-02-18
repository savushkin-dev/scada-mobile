package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Полное состояние SCADA системы (snapshot всех units)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryStateResponseDTO(
        @Schema(description = "Имя устройства", example = "Line")
        @JsonProperty("DeviceName") String deviceName,
        @Schema(description = "Карта состояний units (ключ: u1, u2, u3...)")
        @JsonProperty("Units") Map<String, UnitStateDTO> units
) {
    public QueryStateResponseDTO {
        units = units == null ? Map.of() : Map.copyOf(units);
    }
}
