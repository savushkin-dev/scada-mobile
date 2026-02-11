package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Domain model representing a snapshot of a SCADA device's complete state.
 * <p>
 * This is a pure domain model that captures the state of all units of a device
 * at a specific point in time. It is independent of:
 * <ul>
 *   <li>Transport protocols (PrintSrv, REST)</li>
 *   <li>Serialization mechanisms (JSON, XML)</li>
 *   <li>Framework dependencies (Spring, Jackson)</li>
 * </ul>
 * <p>
 * Invariants enforced by this class:
 * <ul>
 *   <li>Device name cannot be null or empty</li>
 *   <li>Units map cannot be null (but may be empty for devices without units)</li>
 * </ul>
 * <p>
 * This class is immutable and thread-safe.
 */
public final class DeviceSnapshot {
    private final String deviceName;
    private final Map<String, UnitSnapshot> units;

    /**
     * Creates a new device snapshot.
     *
     * @param deviceName name of the device (must not be null or empty)
     * @param units      map of unit snapshots by unit key (e.g., "u1", "u2")
     * @throws IllegalArgumentException if invariants are violated
     */
    public DeviceSnapshot(String deviceName, Map<String, UnitSnapshot> units) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Device name cannot be null or empty");
        }
        if (units == null) {
            throw new IllegalArgumentException("Units map cannot be null");
        }

        this.deviceName = deviceName;
        // Create immutable copy to ensure thread safety
        this.units = Map.copyOf(units);
    }

    /**
     * Gets the device name.
     *
     * @return device name (never null or empty)
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Gets the map of unit snapshots.
     *
     * @return immutable map of units (never null, but may be empty)
     */
    public Map<String, UnitSnapshot> getUnits() {
        return units;
    }

    /**
     * Gets the snapshot for a specific unit.
     *
     * @param unitKey the unit key (e.g., "u1", "u2")
     * @return the unit snapshot, or null if not found
     */
    public UnitSnapshot getUnit(String unitKey) {
        return units.get(unitKey);
    }

    /**
     * Checks if this snapshot contains a specific unit.
     *
     * @param unitKey the unit key to check
     * @return true if the unit exists in this snapshot
     */
    public boolean hasUnit(String unitKey) {
        return units.containsKey(unitKey);
    }

    /**
     * Gets the number of units in this snapshot.
     *
     * @return number of units
     */
    public int getUnitCount() {
        return units.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceSnapshot that = (DeviceSnapshot) o;
        return deviceName.equals(that.deviceName)
                && units.equals(that.units);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceName, units);
    }

    @Override
    public String toString() {
        return "DeviceSnapshot{" +
                "deviceName='" + deviceName + '\'' +
                ", units=" + units.keySet() +
                ", unitCount=" + units.size() +
                '}';
    }
}
