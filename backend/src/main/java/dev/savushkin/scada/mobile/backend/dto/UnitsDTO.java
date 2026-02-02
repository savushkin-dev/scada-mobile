package dev.savushkin.scada.mobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

/**
 * DTO для юнита в ответах PrintSrv.
 * <p>
 * ВАЖНО: Поле counter является Optional потому что:
 * - QueryAll_response: содержит Counter
 * - SetUnitVars_response: НЕ содержит Counter (отсутствует в JSON)
 * <p>
 * JsonInclude(NON_NULL) гарантирует, что Null поля не сериализуются в JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnitsDTO {
    private final String state;
    private final String task;
    private final Integer counter;  // Integer (not int) - может быть null в SetUnitVars ответе
    private final PropertiesDTO properties;

    @JsonCreator
    public UnitsDTO(
            @JsonProperty("State") String state,
            @JsonProperty("Task") String task,
            @JsonProperty("Counter") Integer counter,
            @JsonProperty("Properties") PropertiesDTO properties) {
        this.state = state;
        this.task = task;
        this.counter = counter;
        this.properties = properties;
    }

    public String getState() {
        return state;
    }

    public String getTask() {
        return task;
    }

    /**
     * Счётчик операций юнита.
     * Optional потому что в ответе SetUnitVars это поле отсутствует.
     */
    public Optional<Integer> getCounter() {
        return Optional.ofNullable(counter);
    }

    public PropertiesDTO getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UnitsDTO that)) return false;
        return Objects.equals(counter, that.counter)
                && Objects.equals(state, that.state)
                && Objects.equals(task, that.task)
                && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, task, counter, properties);
    }

    @Override
    public String toString() {
        return "UnitsDTO{" +
                "state='" + state + '\'' +
                ", task='" + task + '\'' +
                ", counter=" + counter +
                ", properties=" + properties +
                '}';
    }
}
