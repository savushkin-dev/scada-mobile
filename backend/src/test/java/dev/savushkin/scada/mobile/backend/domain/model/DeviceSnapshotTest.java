package dev.savushkin.scada.mobile.backend.domain.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для DeviceSnapshot.
 * Проверяет инварианты: deviceName не null/пустое, units не null, иммутабельная копия map.
 */
class DeviceSnapshotTest {

    private static final UnitProperties PROPS = UnitProperties.builder().command(1).build();
    private static final UnitSnapshot UNIT = new UnitSnapshot(1, "", "", null, PROPS);

    // -------------------------------------------------------------------------
    // Допустимые значения
    // -------------------------------------------------------------------------

    @Test
    void validSnapshot_storesDeviceNameAndUnits() {
        DeviceSnapshot snap = new DeviceSnapshot("Line", Map.of("u1", UNIT));

        assertEquals("Line", snap.deviceName());
        assertEquals(1, snap.units().size());
        assertTrue(snap.units().containsKey("u1"));
    }

    @Test
    void emptyUnitsMap_isAllowed() {
        DeviceSnapshot snap = new DeviceSnapshot("Line", Map.of());

        assertEquals(0, snap.getUnitCount());
        assertTrue(snap.units().isEmpty());
    }

    @Test
    void getUnitCount_returnsCorrectCount() {
        DeviceSnapshot snap = new DeviceSnapshot("Device", Map.of(
                "u1", UNIT,
                "u2", new UnitSnapshot(2, "", "", null, PROPS)
        ));

        assertEquals(2, snap.getUnitCount());
    }

    // -------------------------------------------------------------------------
    // Инварианты deviceName
    // -------------------------------------------------------------------------

    @Test
    void nullDeviceName_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceSnapshot(null, Map.of()));
    }

    @Test
    void emptyDeviceName_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceSnapshot("", Map.of()));
    }

    @Test
    void blankDeviceName_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceSnapshot("   ", Map.of()));
    }

    // -------------------------------------------------------------------------
    // Инварианты units
    // -------------------------------------------------------------------------

    @Test
    void nullUnitsMap_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeviceSnapshot("Line", null));
    }

    // -------------------------------------------------------------------------
    // Иммутабельность
    // -------------------------------------------------------------------------

    @Test
    void units_returnedMapIsImmutable() {
        DeviceSnapshot snap = new DeviceSnapshot("Line", Map.of("u1", UNIT));

        assertThrows(UnsupportedOperationException.class,
                () -> snap.units().put("u2", UNIT));
    }

    @Test
    void originalMapMutation_doesNotAffectSnapshot() {
        HashMap<String, UnitSnapshot> mutable = new HashMap<>();
        mutable.put("u1", UNIT);

        DeviceSnapshot snap = new DeviceSnapshot("Line", mutable);

        // Мутируем оригинальный map после создания snapshot
        mutable.put("u2", new UnitSnapshot(2, "", "", null, PROPS));

        // Snapshot не должен видеть изменение
        assertEquals(1, snap.getUnitCount());
    }
}
