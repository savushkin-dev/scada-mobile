package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Objects;

/**
 * Domain model representing a snapshot of a SCADA unit's state at a point in time.
 * <p>
 * This is a pure domain model that captures the essence of a unit's operational state.
 * It is independent of:
 * <ul>
 *   <li>Transport protocols (PrintSrv, REST)</li>
 *   <li>Serialization mechanisms (JSON, XML)</li>
 *   <li>Framework dependencies (Spring, Jackson)</li>
 * </ul>
 * <p>
 * Invariants enforced by this class:
 * <ul>
 *   <li>Unit number must be positive (1-based indexing)</li>
 *   <li>State, task, and properties cannot be null (use empty values if not applicable)</li>
 * </ul>
 * <p>
 * This class is immutable and thread-safe.
 */
public final class UnitSnapshot {
    private final int unitNumber;
    private final String state;
    private final String task;
    private final Integer counter;
    private final UnitProperties properties;

    /**
     * Creates a new unit snapshot.
     *
     * @param unitNumber unit number (1-based, must be >= 1)
     * @param state      current state of the unit (must not be null)
     * @param task       current task of the unit (must not be null)
     * @param counter    operation counter (may be null for some operations)
     * @param properties unit properties (must not be null)
     * @throws IllegalArgumentException if invariants are violated
     */
    public UnitSnapshot(int unitNumber, String state, String task, Integer counter, UnitProperties properties) {
        if (unitNumber < 1) {
            throw new IllegalArgumentException("Unit number must be >= 1, got: " + unitNumber);
        }
        if (state == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }

        this.unitNumber = unitNumber;
        this.state = state;
        this.task = task;
        this.counter = counter;
        this.properties = properties;
    }

    /**
     * Gets the unit number.
     *
     * @return unit number (1-based)
     */
    public int getUnitNumber() {
        return unitNumber;
    }

    /**
     * Gets the current state of the unit.
     *
     * @return state (never null)
     */
    public String getState() {
        return state;
    }

    /**
     * Gets the current task of the unit.
     *
     * @return task (never null)
     */
    public String getTask() {
        return task;
    }

    /**
     * Gets the operation counter.
     *
     * @return counter or null if not available
     */
    public Integer getCounter() {
        return counter;
    }

    /**
     * Gets the unit properties.
     *
     * @return properties (never null)
     */
    public UnitProperties getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitSnapshot that = (UnitSnapshot) o;
        return unitNumber == that.unitNumber
                && state.equals(that.state)
                && task.equals(that.task)
                && Objects.equals(counter, that.counter)
                && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitNumber, state, task, counter, properties);
    }

    @Override
    public String toString() {
        return "UnitSnapshot{" +
                "unitNumber=" + unitNumber +
                ", state='" + state + '\'' +
                ", task='" + task + '\'' +
                ", counter=" + counter +
                ", properties=" + properties +
                '}';
    }
}
