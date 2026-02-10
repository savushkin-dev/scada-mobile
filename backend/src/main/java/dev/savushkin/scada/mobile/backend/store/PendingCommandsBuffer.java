package dev.savushkin.scada.mobile.backend.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe буфер для накопления команд записи в PrintSrv.
 * <p>
 * Используется для изоляции клиентских запросов от scan cycle:
 * <ul>
 *   <li>REST-потоки добавляют команды через {@link #add(PendingWriteCommand)}</li>
 *   <li>Scheduler-поток забирает команды через {@link #getAndClear()}</li>
 * </ul>
 * <p>
 * Архитектурные решения:
 * <ul>
 *   <li><b>Map вместо Queue</b>: Last-Write-Wins для одного unit (нет дубликатов)</li>
 *   <li><b>ConcurrentHashMap</b>: thread-safety без explicit locks</li>
 *   <li><b>MAX_BUFFER_SIZE</b>: защита от переполнения при недоступности PrintSrv</li>
 * </ul>
 * <p>
 * Команды, добавленные во время выполнения {@link #getAndClear()}, попадут в следующий цикл.
 */
@Component
public class PendingCommandsBuffer {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandsBuffer.class);

    /**
     * Максимальный размер буфера.
     * Защищает от переполнения памяти при длительной недоступности PrintSrv.
     */
    private static final int MAX_BUFFER_SIZE = 100;

    /**
     * Буфер команд: ключ = unitId, значение = команда.
     * ConcurrentHashMap обеспечивает thread-safety для concurrent add() и getAndClear().
     */
    private final ConcurrentHashMap<Integer, PendingWriteCommand> buffer = new ConcurrentHashMap<>();

    /**
     * Добавляет команду в буфер.
     * <p>
     * Если для данного unit уже есть команда, она будет заменена (Last-Write-Wins).
     * Это гарантирует, что в PrintSrv будет записано актуальное значение.
     *
     * @param command команда для добавления
     * @throws IllegalStateException если буфер переполнен (размер >= MAX_BUFFER_SIZE)
     * @throws NullPointerException  если command == null
     */
    public void add(PendingWriteCommand command) {
        if (command == null) {
            throw new NullPointerException("Command cannot be null");
        }

        // Проверка переполнения ДО добавления
        // Это предотвращает бесконечное накопление команд при недоступности PrintSrv
        if (buffer.size() >= MAX_BUFFER_SIZE && !buffer.containsKey(command.unit())) {
            String errorMsg = String.format(
                    "Буфер команд переполнен (size=%d, max=%d). PrintSrv недоступен длительное время.",
                    buffer.size(), MAX_BUFFER_SIZE
            );
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // putIfAbsent + replace для атомарной замены
        PendingWriteCommand previous = buffer.put(command.unit(), command);

        if (previous == null) {
            log.debug("Added new pending command for unit {}: {}", command.unit(), command.properties());
        } else {
            log.debug("Replaced pending command for unit {} (Last-Write-Wins): {} -> {}",
                    command.unit(), previous.properties(), command.properties());
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
    public Map<Integer, PendingWriteCommand> getAndClear() {
        // Создаем snapshot перед очисткой
        Map<Integer, PendingWriteCommand> snapshot = new HashMap<>(buffer);

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
