package dev.savushkin.scada.mobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

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
public record ParametersDTO(int command) {

    /**
     * Конструктор для десериализации JSON.
     *
     * @param command значение команды
     */
    @JsonCreator
    public ParametersDTO(
            @JsonProperty("command") int command
    ) {
        this.command = command;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParametersDTO(int command1))) return false;
        return command == command1;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(command);
    }

    @Override
    public @NonNull String toString() {
        return "ParametersDTO{" +
                "command=" + command +
                '}';
    }
}
