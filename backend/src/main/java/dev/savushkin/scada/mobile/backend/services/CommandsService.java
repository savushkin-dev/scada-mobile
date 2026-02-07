package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.client.SetUnitVars;
import dev.savushkin.scada.mobile.backend.dto.ParametersDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsResponseDTO;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Сервис для предоставления данных клиентам через REST API.
 * <p>
 * Основные функции:
 * <ul>
 *   <li><b>QueryAll</b>: читает snapshot из Store (обновляется автоматически через Scheduler)</li>
 *   <li><b>SetUnitVars</b>: выполняет запись в PrintSrv (Store обновится при следующем polling)</li>
 * </ul>
 * <p>
 * Сервис работает с двумя источниками данных:
 * <ol>
 *   <li>PrintSrvSnapshotStore - для быстрого чтения состояния</li>
 *   <li>SetUnitVars команда - для синхронной записи в PrintSrv</li>
 * </ol>
 */
@Service
public class CommandsService {

    private static final Logger log = LoggerFactory.getLogger(CommandsService.class);

    private final PrintSrvSnapshotStore snapshotStore;
    private final SetUnitVars setUnitVarsCommand;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param snapshotStore      хранилище snapshot состояния PrintSrv
     * @param setUnitVarsCommand команда для изменения значений в PrintSrv
     */
    public CommandsService(
            PrintSrvSnapshotStore snapshotStore,
            SetUnitVars setUnitVarsCommand
    ) {
        this.snapshotStore = snapshotStore;
        this.setUnitVarsCommand = setUnitVarsCommand;
        log.info("CommandsService initialized with PrintSrvSnapshotStore and SetUnitVars command");
    }

    /**
     * Получает текущий snapshot состояния PrintSrv из хранилища.
     * <p>
     * Данные синхронизируются автоматически через ScadaDataPollingService
     * каждые 500ms, поэтому метод возвращает актуальное (но не real-time) состояние.
     *
     * @return snapshot состояния PrintSrv со всеми units и их свойствами
     * @throws IllegalStateException если snapshot ещё не загружен (приложение только запустилось)
     */
    public QueryAllResponseDTO queryAll() {
        log.debug("Reading snapshot from store");
        // Получаем последний snapshot
        QueryAllResponseDTO snapshot = snapshotStore.getSnapshot();

        // Проверяем, что данные уже загружены
        if (snapshot == null) {
            log.warn("Snapshot not available - store is empty");
            throw new IllegalStateException("PrintSrv snapshot not available yet. Please wait for the first polling cycle.");
        }

        log.debug("Snapshot retrieved successfully with {} units", snapshot.units().size());
        return snapshot;
    }

    /**
     * Выполняет команду SetUnitVars для изменения значения в PrintSrv.
     * <p>
     * Команда выполняется синхронно через socket-соединение с PrintSrv.
     * Обновленное состояние появится в Store автоматически при следующем
     * polling цикле (через ~500ms).
     *
     * @param unit  номер юнита (1-based, например: 1 = u1, 2 = u2)
     * @param value новое значение команды (целое число)
     * @return результат выполнения команды (частичный ответ с измененными полями)
     * @throws IOException если произошла ошибка связи с PrintSrv
     */
    public SetUnitVarsResponseDTO setUnitVars(int unit, int value) throws IOException {
        log.info("Executing SetUnitVars command: unit={}, value={}", unit, value);

        // Создаем Parameters с новым значением
        ParametersDTO parameters = new ParametersDTO(value);

        // Формируем полный запрос для PrintSrv
        SetUnitVarsRequestDTO request = new SetUnitVarsRequestDTO(
                "Line",         // DeviceName
                unit,           // Unit (1-based)
                "SetUnitVars",  // Command
                parameters      // Parameters с новым значением
        );
        log.debug("Created SetUnitVarsRequestDTO: {}", request);

        // Выполняем команду через socket
        SetUnitVarsResponseDTO response = setUnitVarsCommand.execute(request);
        log.info("SetUnitVars executed successfully: unit={}, value={}, response units count={}",
                unit, value, response.units().size());
        log.debug("Snapshot will be updated automatically on next polling cycle");

        return response;
    }
}
