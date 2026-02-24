package dev.savushkin.scada.mobile.backend.domain.policy;

import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для LastWriteWinsPerUnitPolicy.
 * Проверяет: замена команды для того же unit (LWW), добавление нового unit,
 * возврат предыдущей команды, null-гарантии.
 */
class LastWriteWinsPerUnitPolicyTest {

    private LastWriteWinsPerUnitPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LastWriteWinsPerUnitPolicy();
    }

    // -------------------------------------------------------------------------
    // Штатный сценарий: первая команда для unit
    // -------------------------------------------------------------------------

    @Test
    void apply_firstCommandForUnit_returnsPreviousNull() {
        Map<Integer, WriteCommand> map = new HashMap<>();
        WriteCommand cmd = new WriteCommand(100L, 1, 10);

        WriteCommand previous = policy.apply(map, cmd);

        assertNull(previous, "no previous command for new unit");
        assertEquals(cmd, map.get(1));
    }

    @Test
    void apply_firstCommandForUnit_isStoredInMap() {
        Map<Integer, WriteCommand> map = new HashMap<>();
        WriteCommand cmd = new WriteCommand(100L, 2, 20);

        policy.apply(map, cmd);

        assertEquals(cmd, map.get(2));
    }

    // -------------------------------------------------------------------------
    // LWW: замена предыдущей команды для того же unit
    // -------------------------------------------------------------------------

    @Test
    void apply_secondCommandForSameUnit_returnsPreviousCommand() {
        Map<Integer, WriteCommand> map = new HashMap<>();
        WriteCommand first = new WriteCommand(100L, 1, 10);
        WriteCommand second = new WriteCommand(200L, 1, 99);

        policy.apply(map, first);
        WriteCommand previous = policy.apply(map, second);

        assertEquals(first, previous, "previous command must be returned");
    }

    @Test
    void apply_secondCommandForSameUnit_replacesOldCommand() {
        Map<Integer, WriteCommand> map = new HashMap<>();
        WriteCommand first = new WriteCommand(100L, 1, 10);
        WriteCommand second = new WriteCommand(200L, 1, 99);

        policy.apply(map, first);
        policy.apply(map, second);

        assertEquals(second, map.get(1), "map must store the latest command (LWW)");
    }

    // -------------------------------------------------------------------------
    // Несколько units не конфликтуют
    // -------------------------------------------------------------------------

    @Test
    void apply_differentUnits_eachStoredIndependently() {
        Map<Integer, WriteCommand> map = new HashMap<>();
        WriteCommand cmd1 = new WriteCommand(100L, 1, 10);
        WriteCommand cmd2 = new WriteCommand(100L, 2, 20);

        policy.apply(map, cmd1);
        policy.apply(map, cmd2);

        assertEquals(cmd1, map.get(1));
        assertEquals(cmd2, map.get(2));
        assertEquals(2, map.size());
    }

    // -------------------------------------------------------------------------
    // Null-гарантии
    // -------------------------------------------------------------------------

    @Test
    void apply_nullCurrentMap_throwsIllegalArgument() {
        WriteCommand cmd = new WriteCommand(100L, 1, 10);

        assertThrows(IllegalArgumentException.class,
                () -> policy.apply(null, cmd));
    }

    @Test
    void apply_nullIncomingCommand_throwsIllegalArgument() {
        Map<Integer, WriteCommand> map = new HashMap<>();

        assertThrows(IllegalArgumentException.class,
                () -> policy.apply(map, null));
    }
}
