package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для PrintSrvSnapshotStore.
 * Проверяет: инициализацию (null), сохранение/чтение snapshot, атомарную замену.
 */
class PrintSrvSnapshotStoreTest {

    private PrintSrvSnapshotStore store;

    @BeforeEach
    void setUp() {
        store = new PrintSrvSnapshotStore();
    }

    // -------------------------------------------------------------------------
    // Инициализация
    // -------------------------------------------------------------------------

    @Test
    void afterInit_getLatestOrNull_returnsNull() {
        assertNull(store.getLatestOrNull());
    }

    @Test
    void afterInit_getSnapshot_returnsNull() {
        assertNull(store.getSnapshot());
    }

    // -------------------------------------------------------------------------
    // saveSnapshot / getSnapshot
    // -------------------------------------------------------------------------

    @Test
    void saveSnapshot_thenGet_returnsSameSnapshot() {
        DeviceSnapshot snap = new DeviceSnapshot("Line", Map.of());
        store.saveSnapshot(snap);

        assertSame(snap, store.getSnapshot());
    }

    @Test
    void save_delegatesToSaveSnapshot() {
        DeviceSnapshot snap = new DeviceSnapshot("Device", Map.of());
        store.save(snap);

        assertSame(snap, store.getLatestOrNull());
    }

    @Test
    void saveSnapshot_replacesPreviousSnapshot() {
        DeviceSnapshot first = new DeviceSnapshot("First", Map.of());
        DeviceSnapshot second = new DeviceSnapshot("Second", Map.of());

        store.saveSnapshot(first);
        store.saveSnapshot(second);

        assertSame(second, store.getSnapshot());
    }

    // -------------------------------------------------------------------------
    // getLatestOrNull
    // -------------------------------------------------------------------------

    @Test
    void getLatestOrNull_afterSave_returnsSnapshot() {
        DeviceSnapshot snap = new DeviceSnapshot("Line", Map.of());
        store.saveSnapshot(snap);

        assertSame(snap, store.getLatestOrNull());
    }

    // -------------------------------------------------------------------------
    // Проверяем что DeviceSnapshot.getUnitCount() корректен при чтении из store
    // -------------------------------------------------------------------------

    @Test
    void save_snapshotWithUnits_unitCountIsPreserved() {
        DeviceSnapshot snap = new DeviceSnapshot("Line", Map.of(
                "u1", new dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot(
                        1, "", "", null,
                        dev.savushkin.scada.mobile.backend.domain.model.UnitProperties.builder().build()
                )
        ));
        store.saveSnapshot(snap);

        assertEquals(1, store.getLatestOrNull().getUnitCount());
    }
}
