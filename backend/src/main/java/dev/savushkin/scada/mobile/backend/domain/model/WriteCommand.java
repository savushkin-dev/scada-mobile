package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Domain model representing a write command to a SCADA unit.
 * <p>
 * This is a pure domain model that represents the business concept of
 * commanding a SCADA unit to perform an action. It is independent of:
 * <ul>
 *   <li>Transport protocols (PrintSrv, REST)</li>
 *   <li>Serialization mechanisms (JSON, XML)</li>
 *   <li>Framework dependencies (Spring, Jackson)</li>
 * </ul>
 * <p>
 * Invariants enforced by this class:
 * <ul>
 *   <li>Unit number must be positive (1-based indexing)</li>
 *   <li>Properties map cannot be null or empty</li>
 *   <li>Timestamp is always set and cannot be negative</li>
 * </ul>
 * <p>
 * This class is immutable and thread-safe.
 */
public final class WriteCommand {
    private final long timestamp;
    private final int unitNumber;
    private final Map<String, Object> properties;

    /**
     * Creates a new write command with the current timestamp.
     *
     * @param unitNumber unit number (1-based, must be >= 1)
     * @param properties properties to write (must not be null or empty)
     * @throws IllegalArgumentException if invariants are violated
     */
    public WriteCommand(int unitNumber, Map<String, Object> properties) {
        this(System.currentTimeMillis(), unitNumber, properties);
    }

    /**
     * Creates a new write command with an explicit timestamp.
     *
     * @param timestamp  timestamp in milliseconds since epoch
     * @param unitNumber unit number (1-based, must be >= 1)
     * @param properties properties to write (must not be null or empty)
     * @throws IllegalArgumentException if invariants are violated
     */
    public WriteCommand(long timestamp, int unitNumber, Map<String, Object> properties) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative: " + timestamp);
        }
        if (unitNumber < 1) {
            throw new IllegalArgumentException("Unit number must be >= 1, got: " + unitNumber);
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties cannot be null or empty");
        }

        this.timestamp = timestamp;
        this.unitNumber = unitNumber;
        // Create immutable copy to ensure thread safety
        this.properties = Map.copyOf(properties);
    }

    /**
     * Gets the timestamp when this command was created.
     *
     * @return timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the unit number this command targets.
     *
     * @return unit number (1-based)
     */
    public int getUnitNumber() {
        return unitNumber;
    }

    /**
     * Gets the properties to write.
     *
     * @return immutable map of properties
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WriteCommand that = (WriteCommand) o;
        return timestamp == that.timestamp
                && unitNumber == that.unitNumber
                && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, unitNumber, properties);
    }

    @Override
    public String toString() {
        return "WriteCommand{" +
                "timestamp=" + timestamp +
                ", unitNumber=" + unitNumber +
                ", properties=" + properties +
                '}';
    }
}
