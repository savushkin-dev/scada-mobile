package dev.savushkin.scada.mobile.backend.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для UnitSnapshot.
 * Проверяет инварианты записи: unitNumber >= 1, state/task/properties не null.
 */
class UnitSnapshotTest {

    private static final UnitProperties EMPTY_PROPS = UnitProperties.builder().build();

    // -------------------------------------------------------------------------
    // Допустимые значения
    // -------------------------------------------------------------------------

    @Test
    void validSnapshot_storesAllFields() {
        UnitSnapshot snap = new UnitSnapshot(1, "Run", "PrintLabel", 42, EMPTY_PROPS);

        assertEquals(1, snap.unitNumber());
        assertEquals("Run", snap.state());
        assertEquals("PrintLabel", snap.task());
        assertEquals(42, snap.counter());
        assertSame(EMPTY_PROPS, snap.properties());
    }

    @Test
    void validSnapshot_counterNull_isAllowed() {
        assertDoesNotThrow(() -> new UnitSnapshot(1, "", "", null, EMPTY_PROPS));
    }

    @Test
    void validSnapshot_emptyStrings_areAllowed() {
        assertDoesNotThrow(() -> new UnitSnapshot(5, "", "", 0, EMPTY_PROPS));
    }

    // -------------------------------------------------------------------------
    // Инварианты
    // -------------------------------------------------------------------------

    @Test
    void unitNumberZero_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new UnitSnapshot(0, "Run", "Task", null, EMPTY_PROPS));
    }

    @Test
    void unitNumberNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new UnitSnapshot(-3, "Run", "Task", null, EMPTY_PROPS));
    }

    @Test
    void nullState_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new UnitSnapshot(1, null, "Task", null, EMPTY_PROPS));
    }

    @Test
    void nullTask_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new UnitSnapshot(1, "Run", null, null, EMPTY_PROPS));
    }

    @Test
    void nullProperties_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new UnitSnapshot(1, "Run", "Task", null, null));
    }

    // -------------------------------------------------------------------------
    // Record: equality
    // -------------------------------------------------------------------------

    @Test
    void equalSnapshots_areEqual() {
        UnitSnapshot a = new UnitSnapshot(1, "s", "t", 5, EMPTY_PROPS);
        UnitSnapshot b = new UnitSnapshot(1, "s", "t", 5, EMPTY_PROPS);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentUnitNumber_notEqual() {
        UnitSnapshot a = new UnitSnapshot(1, "s", "t", 5, EMPTY_PROPS);
        UnitSnapshot b = new UnitSnapshot(2, "s", "t", 5, EMPTY_PROPS);

        assertNotEquals(a, b);
    }
}
