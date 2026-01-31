package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class UnitsDTO {
    private final String state;
    private final String task;
    private final int counter;
    private final PropertiesDTO properties;

    @JsonCreator
    public UnitsDTO(
            @JsonProperty("State") String state,
            @JsonProperty("Task") String task,
            @JsonProperty("Counter") int counter,
            @JsonProperty("Properties") PropertiesDTO properties
    ) {
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

    public int getCounter() {
        return counter;
    }

    public PropertiesDTO getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UnitsDTO unitsDTO)) return false;
        return counter == unitsDTO.counter && Objects.equals(state, unitsDTO.state) && Objects.equals(task, unitsDTO.task) && Objects.equals(properties, unitsDTO.properties);
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
