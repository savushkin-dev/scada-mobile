package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.dto.*;
import dev.savushkin.scada.mobile.backend.store.PendingCommandsBuffer;
import dev.savushkin.scada.mobile.backend.store.PendingWriteCommand;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Сервис для предоставления данных клиентам через REST API.
 * <p>
 * Основные функции:
 * <ul>
 *   <li><b>QueryAll</b>: читает snapshot из Store (обновляется автоматически через Scan Cycle)</li>
 *   <li><b>SetUnitVars</b>: добавляет команду в буфер (будет выполнена в следующем Scan Cycle)</li>
 * </ul>
 * <p>
 * Архитектура Write-Through Cache:
 * <ol>
 *   <li>Клиент отправляет POST /set → команда добавляется в буфер (быстрый ответ < 50ms)</li>
 *   <li>Scan Cycle забирает команды из буфера и записывает в PrintSrv (каждые 5 сек)</li>
 *   <li>Клиент получает актуальные данные через GET /query (snapshot обновляется после каждого цикла)</li>
 * </ol>
 * <p>
 * Это обеспечивает:
 * <ul>
 *   <li><b>Быстрый отклик</b>: клиент не ждет PrintSrv</li>
 *   <li><b>Eventual Consistency</b>: изменения видны в течение 5 секунд</li>
 *   <li><b>Безопасность</b>: нет race conditions (один поток пишет в PrintSrv)</li>
 * </ul>
 */
@Service
public class CommandsService {

    private static final Logger log = LoggerFactory.getLogger(CommandsService.class);

    private final PrintSrvSnapshotStore snapshotStore;
    private final PendingCommandsBuffer pendingCommandsBuffer;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param snapshotStore          хранилище snapshot состояния PrintSrv
     * @param pendingCommandsBuffer  буфер pending команд для записи
     */
    public CommandsService(
            PrintSrvSnapshotStore snapshotStore,
            PendingCommandsBuffer pendingCommandsBuffer
    ) {
        this.snapshotStore = snapshotStore;
        this.pendingCommandsBuffer = pendingCommandsBuffer;
        log.info("CommandsService initialized with Write-Through Cache architecture");
    }

    /**
     * Получает текущий snapshot состояния PrintSrv из хранилища.
     * <p>
     * Данные синхронизируются автоматически через
     * {@link dev.savushkin.scada.mobile.backend.services.polling.PrintSrvPollingScheduler}
     * с интервалом scan cycle (настраивается через <code>printsrv.polling.fixed-delay-ms</code>).
     * <p>
     * Snapshot содержит актуальное состояние PrintSrv на момент последнего цикла.
     * Изменения, сделанные через {@link #setUnitVars(int, int)}, появятся здесь
     * после следующего scan cycle (до 5 секунд задержки).
     *
     * @return snapshot состояния PrintSrv со всеми units и их свойствами
     * @throws IllegalStateException если snapshot ещё не загружен (приложение только запустилось)
     */
    public QueryAllResponseDTO queryAll() {
        log.debug("Reading snapshot from store");
        QueryAllResponseDTO snapshot = snapshotStore.getSnapshot();

        if (snapshot == null) {
            log.warn("Snapshot not available - store is empty");
            throw new IllegalStateException("PrintSrv snapshot not available yet. Please wait for the first scan cycle.");
        }

        log.debug("Snapshot retrieved successfully with {} units", snapshot.units().size());
        return snapshot;
    }

    /**
     * Добавляет команду SetUnitVars в буфер для выполнения в следующем Scan Cycle.
     * <p>
     * Метод возвращает управление немедленно (< 50ms), не дожидаясь записи в PrintSrv.
     * Команда будет выполнена в следующем scan cycle (до 5 секунд задержки).
     * <p>
     * Клиент может проверить результат выполнения через {@link #queryAll()}
     * после следующего scan cycle.
     * <p>
     * Архитектурные гарантии:
     * <ul>
     *   <li><b>Last-Write-Wins</b>: если для одного unit отправлено несколько команд,
     *       в PrintSrv будет записана только последняя</li>
     *   <li><b>Eventual Consistency</b>: изменения видны через ≤ 5 секунд</li>
     *   <li><b>No Retry</b>: если запись в PrintSrv не удалась, команда теряется
     *       (клиент может повторить запрос)</li>
     * </ul>
     *
     * @param unit  номер юнита (1-based, например: 1 = u1, 2 = u2)
     * @param value новое значение команды (целое число)
     * @return acknowledgment ответ с переданными значениями (НЕ реальное состояние из PrintSrv)
     * @throws IllegalStateException если буфер переполнен (PrintSrv недоступен длительное время)
     */
    public SetUnitVarsResponseDTO setUnitVars(int unit, int value) {
        log.info("Adding SetUnitVars command to buffer: unit={}, value={}", unit, value);

        // Создаем pending команду
        PendingWriteCommand command = new PendingWriteCommand(
                unit,
                Map.of("command", value)
        );

        // Добавляем в буфер (будет обработана в следующем scan cycle)
        pendingCommandsBuffer.add(command);
        log.debug("Command added to buffer successfully (buffer size={})", pendingCommandsBuffer.size());

        // Возвращаем acknowledgment ответ (БЕЗ реальных данных из PrintSrv)
        // Клиент узнает результат при следующем GET /query-all
        PropertiesDTO properties = new PropertiesDTO(
                value,  // command value
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );

        UnitsDTO unitDTO = new UnitsDTO(
                null,       // state - не известно
                null,       // task - не известно
                null,       // counter - не известно
                properties
        );

        log.info("SetUnitVars command accepted: unit={}, value={} (will be executed in next scan cycle)",
                unit, value);

        return new SetUnitVarsResponseDTO(
                "Line",
                "SetUnitVars",
                Map.of("u" + unit, unitDTO)
        );
    }
}
