package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryInstanceSnapshotStoreTest {

    private InMemoryInstanceSnapshotStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryInstanceSnapshotStore();
    }

    @Test
    void initiallyEmpty() {
        assertFalse(store.hasAnySnapshot());
        assertNull(store.get("trepko1", "Line"));
        assertTrue(store.getAllForInstance("trepko1").isEmpty());
    }

    @Test
    void saveAndGet_singleDevice() {
        DeviceSnapshot snapshot = createSnapshot("Line", 1);
        store.save("trepko1", "Line", snapshot);

        assertTrue(store.hasAnySnapshot());
        assertSame(snapshot, store.get("trepko1", "Line"));
    }

    @Test
    void saveAndGet_multipleDevicesPerInstance() {
        DeviceSnapshot line = createSnapshot("Line", 1);
        DeviceSnapshot scada = createSnapshot("scada", 2);

        store.save("trepko1", "Line", line);
        store.save("trepko1", "scada", scada);

        assertSame(line, store.get("trepko1", "Line"));
        assertSame(scada, store.get("trepko1", "scada"));
        assertEquals(2, store.getAllForInstance("trepko1").size());
    }

    @Test
    void saveAndGet_multipleInstances() {
        DeviceSnapshot snap1 = createSnapshot("Line", 1);
        DeviceSnapshot snap2 = createSnapshot("Line", 2);

        store.save("trepko1", "Line", snap1);
        store.save("hassia2", "Line", snap2);

        assertSame(snap1, store.get("trepko1", "Line"));
        assertSame(snap2, store.get("hassia2", "Line"));
    }

    @Test
    void save_replacesExistingSnapshot() {
        DeviceSnapshot old = createSnapshot("Line", 1);
        DeviceSnapshot updated = createSnapshot("Line", 5);

        store.save("trepko1", "Line", old);
        store.save("trepko1", "Line", updated);

        assertSame(updated, store.get("trepko1", "Line"));
        assertEquals(1, store.getAllForInstance("trepko1").size());
    }

    @Test
    void getAllForInstance_returnsUnmodifiableMap() {
        store.save("trepko1", "Line", createSnapshot("Line", 1));

        Map<String, DeviceSnapshot> all = store.getAllForInstance("trepko1");

        assertThrows(UnsupportedOperationException.class,
                () -> all.put("test", createSnapshot("test", 1)));
    }

    @Test
    void get_unknownInstance_returnsNull() {
        assertNull(store.get("unknown", "Line"));
    }

    @Test
    void get_unknownDevice_returnsNull() {
        store.save("trepko1", "Line", createSnapshot("Line", 1));
        assertNull(store.get("trepko1", "scada"));
    }

    private DeviceSnapshot createSnapshot(String deviceName, int unitNumber) {
        UnitSnapshot unit = new UnitSnapshot(
                unitNumber, "0", "", null, UnitProperties.builder().build());
        return new DeviceSnapshot(deviceName, Map.of("u" + unitNumber, unit));
    }
}
