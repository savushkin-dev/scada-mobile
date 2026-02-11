package dev.savushkin.scada.mobile.backend.printsrv.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO для параметров команды SetUnitVars.
 * <p>
 * Содержит параметры, которые передаются в команду для изменения
 * значений в PrintSrv. В текущей реализации поддерживается только
 * параметр {@code command}.
 * <p>
 * Пример JSON:
 * <pre>
 * {
 *   "command": 555
 * }
 * </pre>
 * <p>
 * <b>Важно:</b> Значение {@code command} должно быть целым числом (int),
 * не строкой. Это требование протокола PrintSrv.
 *
 * @param command новое значение команды (целое число)
 */
public record ParametersDTO(
        @JsonProperty("command") int command
) {
}
