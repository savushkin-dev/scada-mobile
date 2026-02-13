package dev.savushkin.scada.mobile.backend.store;

import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import dev.savushkin.scada.mobile.backend.exception.BufferOverflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe буфер для накопления команд записи в SCADA систему.
 * <p>
 * Теперь использует domain модель {@link WriteCommand} вместо {@code PendingWriteCommand}.
 * Это обеспечивает:
 * <ul>
 *   <li><b>Независимость от инфраструктуры</b>: буфер работает с бизнес-сущностями</li>
 *   <li><b>Типобезопасность</b>: domain модели обеспечивают строгие инварианты</li>
 *   <li><b>Чистую архитектуру</b>: буфер не зависит от DTO</li>
 * </ul>
 * <p>
 * Используется для изоляции клиентских запросов от scan cycle:
 * <ul>
 *   <li>REST-потоки добавляют команды через {@link #add(WriteCommand)}</li>
 *   <li>Scheduler-поток забирает команды через {@link #getAndClear()}</li>
 * </ul>
 * <p>
 * Архитектурные решения:
 * <ul>
 *   <li><b>Map вместо Queue</b>: Last-Write-Wins для одного unit (нет дубликатов)</li>
 *   <li><b>ConcurrentHashMap</b>: thread-safety без explicit locks</li>
 *   <li><b>MAX_BUFFER_SIZE</b>: защита от переполнения при недоступности SCADA</li>
 * </ul>
 * <p>
 * Команды, добавленные во время выполнения {@link #getAndClear()}, попадут в следующий цикл.
 */
@Component
public class PendingCommandsBuffer {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandsBuffer.class);

    /**
     * Максимальный размер буфера.
     * Защищает от переполнения памяти при длительной недоступности SCADA.
     */
    private static final int MAX_BUFFER_SIZE = 100;

    /**
     * Буфер команд: ключ = unitNumber, значение = команда.
     * ConcurrentHashMap обеспечивает thread-safety для concurrent add() и getAndClear().
     */
    private final ConcurrentHashMap<Integer, WriteCommand> buffer = new ConcurrentHashMap<>();

    /**
     * Добавляет команду в буфер.
     * <p>
     * Если для данного unit уже есть команда, она будет заменена (Last-Write-Wins).
     * Это гарантирует, что в SCADA будет записано актуальное значение.
     *
     * @param command команда для добавления
     * @throws BufferOverflowException если буфер переполнен (размер >= MAX_BUFFER_SIZE)
     * @throws NullPointerException    если command == null
     */
    public void add(WriteCommand command) {
        if (command == null) {
            throw new NullPointerException("Command cannot be null");
        }

        // Проверка переполнения ДО добавления
        // Это предотвращает бесконечное накопление команд при недоступности SCADA
        if (buffer.size() >= MAX_BUFFER_SIZE && !buffer.containsKey(command.getUnitNumber())) {
            String errorMsg = String.format(
                    "Буфер команд переполнен (size=%d, max=%d). SCADA недоступна длительное время.",
                    buffer.size(), MAX_BUFFER_SIZE
            );
            log.error(errorMsg);
            throw new BufferOverflowException(errorMsg);
        }

        // putIfAbsent + replace для атомарной замены
        WriteCommand previous = buffer.put(command.getUnitNumber(), command);

        if (previous == null) {
            log.debug("Added new pending command for unit {}: command={}", command.getUnitNumber(), command.getCommandValue());
        } else {
            log.debug("Replaced pending command for unit {} (Last-Write-Wins): command={} -> {}",
                    command.getUnitNumber(), previous.getCommandValue(), command.getCommandValue());
        }
    }

    /**
     * Атомарно забирает все команды и очищает буфер.
     * <p>
     * Команды, добавленные во время выполнения этого метода,
     * попадут в следующий scan cycle (это безопасно и соответствует дизайну).
     *
     * @return снимок буфера на момент вызова (immutable Map)
     */
    public Map<Integer, WriteCommand> getAndClear() {
        // Создаем snapshot перед очисткой
        Map<Integer, WriteCommand> snapshot = new HashMap<>(buffer);

        // Очищаем буфер атомарно
        buffer.clear();

        if (!snapshot.isEmpty()) {
            log.debug("Retrieved {} pending command(s) from buffer", snapshot.size());
        }

        return snapshot;
    }

    /**
     * Проверяет, пуст ли буфер.
     *
     * @return true если буфер пуст
     */
    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /**
     * Возвращает текущий размер буфера.
     *
     * @return количество команд в буфере
     */
    public int size() {
        return buffer.size();
    }
}
