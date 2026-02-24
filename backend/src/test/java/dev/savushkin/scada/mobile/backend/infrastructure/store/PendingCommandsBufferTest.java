package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import dev.savushkin.scada.mobile.backend.domain.policy.LastWriteWinsPerUnitPolicy;
import dev.savushkin.scada.mobile.backend.exception.BufferOverflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для PendingCommandsBuffer.
 * Проверяет: добавление, Last-Write-Wins, переполнение, drain (getAndClear), isEmpty/size.
 */
class PendingCommandsBufferTest {

    private PendingCommandsBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new PendingCommandsBuffer(new LastWriteWinsPerUnitPolicy());
    }

    // -------------------------------------------------------------------------
    // Добавление и базовые методы
    // -------------------------------------------------------------------------

    @Test
    void isEmpty_afterCreation_returnsTrue() {
        assertTrue(buffer.isEmpty());
    }

    @Test
    void size_afterCreation_returnsZero() {
        assertEquals(0, buffer.size());
    }

    @Test
    void add_singleCommand_bufferContainsIt() {
        buffer.add(new WriteCommand(1, 10));

        assertEquals(1, buffer.size());
        assertFalse(buffer.isEmpty());
    }

    @Test
    void enqueue_delegatesToAdd() {
        buffer.enqueue(new WriteCommand(1, 10));

        assertEquals(1, buffer.size());
    }

    @Test
    void add_nullCommand_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> buffer.add(null));
    }

    // -------------------------------------------------------------------------
    // Last-Write-Wins
    // -------------------------------------------------------------------------

    @Test
    void add_twoCommandsForSameUnit_bufferSizeStaysOne() {
        buffer.add(new WriteCommand(100L, 1, 10));
        buffer.add(new WriteCommand(200L, 1, 20));

        assertEquals(1, buffer.size());
    }

    @Test
    void add_twoCommandsForSameUnit_lastCommandWins() {
        buffer.add(new WriteCommand(100L, 1, 10));
        buffer.add(new WriteCommand(200L, 1, 20));

        Map<Integer, WriteCommand> drained = buffer.getAndClear();
        assertEquals(20, drained.get(1).commandValue());
    }

    @Test
    void add_commandsForDifferentUnits_bothStoredIndependently() {
        buffer.add(new WriteCommand(1, 10));
        buffer.add(new WriteCommand(2, 20));

        assertEquals(2, buffer.size());
    }

    // -------------------------------------------------------------------------
    // getAndClear / drain
    // -------------------------------------------------------------------------

    @Test
    void getAndClear_returnsAllCommands() {
        buffer.add(new WriteCommand(1, 10));
        buffer.add(new WriteCommand(2, 20));

        Map<Integer, WriteCommand> drained = buffer.getAndClear();

        assertEquals(2, drained.size());
        assertNotNull(drained.get(1));
        assertNotNull(drained.get(2));
    }

    @Test
    void getAndClear_clearsBuffer() {
        buffer.add(new WriteCommand(1, 10));
        buffer.getAndClear();

        assertTrue(buffer.isEmpty());
    }

    @Test
    void getAndClear_emptyBuffer_returnsEmptyMap() {
        Map<Integer, WriteCommand> drained = buffer.getAndClear();

        assertNotNull(drained);
        assertTrue(drained.isEmpty());
    }

    @Test
    void drain_delegatesToGetAndClear() {
        buffer.add(new WriteCommand(1, 10));
        Map<Integer, WriteCommand> drained = buffer.drain();

        assertEquals(1, drained.size());
        assertTrue(buffer.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Overflow protection
    // -------------------------------------------------------------------------

    @Test
    void add_whenBufferFullWithNewUnit_throwsBufferOverflow() {
        // MAX_BUFFER_SIZE = 100; заполняем 100 команд для разных units
        for (int i = 1; i <= 100; i++) {
            buffer.add(new WriteCommand(i, 1));
        }

        // 101-я команда для нового unit должна вызвать переполнение
        assertThrows(BufferOverflowException.class,
                () -> buffer.add(new WriteCommand(101, 1)));
    }

    @Test
    void add_whenBufferFullButSameUnit_doesNotThrow() {
        // Заполняем буфер до 100
        for (int i = 1; i <= 100; i++) {
            buffer.add(new WriteCommand(i, 1));
        }

        // Обновление существующего unit должно работать (LWW для существующего ключа)
        assertDoesNotThrow(() -> buffer.add(new WriteCommand(1, 99)));
    }
}
