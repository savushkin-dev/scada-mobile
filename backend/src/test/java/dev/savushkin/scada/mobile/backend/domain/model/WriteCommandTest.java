package dev.savushkin.scada.mobile.backend.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для WriteCommand.
 * Проверяет инварианты доменной записи: timestamp, unitNumber, commandValue.
 */
class WriteCommandTest {

    // -------------------------------------------------------------------------
    // Конструктор WriteCommand(int unitNumber, int commandValue)
    // -------------------------------------------------------------------------

    @Test
    void twoArgConstructor_setsFieldsCorrectly() {
        long beforeMs = System.currentTimeMillis();
        WriteCommand cmd = new WriteCommand(2, 128);
        long afterMs = System.currentTimeMillis();

        assertEquals(2, cmd.unitNumber());
        assertEquals(128, cmd.commandValue());
        assertTrue(cmd.timestamp() >= beforeMs && cmd.timestamp() <= afterMs,
                "timestamp must be set to current time");
    }

    @Test
    void twoArgConstructor_unitNumber1_isValid() {
        assertDoesNotThrow(() -> new WriteCommand(1, 1));
    }

    @Test
    void twoArgConstructor_unitNumberZero_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new WriteCommand(0, 128));
    }

    @Test
    void twoArgConstructor_unitNumberNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new WriteCommand(-5, 128));
    }

    // -------------------------------------------------------------------------
    // Canonical constructor WriteCommand(long timestamp, int unitNumber, int commandValue)
    // -------------------------------------------------------------------------

    @Test
    void threeArgConstructor_setsAllFields() {
        WriteCommand cmd = new WriteCommand(1000L, 3, 64);

        assertEquals(1000L, cmd.timestamp());
        assertEquals(3, cmd.unitNumber());
        assertEquals(64, cmd.commandValue());
    }

    @Test
    void threeArgConstructor_timestampZero_isValid() {
        assertDoesNotThrow(() -> new WriteCommand(0L, 1, 1));
    }

    @Test
    void threeArgConstructor_negativeTimestamp_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new WriteCommand(-1L, 1, 128));
    }

    @Test
    void threeArgConstructor_unitNumberZero_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new WriteCommand(100L, 0, 128));
    }

    // -------------------------------------------------------------------------
    // Record: equality и hashCode
    // -------------------------------------------------------------------------

    @Test
    void recordEquality_sameValues_areEqual() {
        WriteCommand a = new WriteCommand(500L, 1, 10);
        WriteCommand b = new WriteCommand(500L, 1, 10);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void recordEquality_differentTimestamp_notEqual() {
        WriteCommand a = new WriteCommand(1L, 1, 10);
        WriteCommand b = new WriteCommand(2L, 1, 10);

        assertNotEquals(a, b);
    }

    @Test
    void recordEquality_differentUnit_notEqual() {
        WriteCommand a = new WriteCommand(100L, 1, 10);
        WriteCommand b = new WriteCommand(100L, 2, 10);

        assertNotEquals(a, b);
    }

    @Test
    void recordEquality_differentCommandValue_notEqual() {
        WriteCommand a = new WriteCommand(100L, 1, 10);
        WriteCommand b = new WriteCommand(100L, 1, 20);

        assertNotEquals(a, b);
    }
}
