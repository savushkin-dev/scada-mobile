package dev.savushkin.scada.mobile.backend.services.polling;

import dev.savushkin.scada.mobile.backend.client.QueryAllCommand;
import dev.savushkin.scada.mobile.backend.client.SetUnitVars;
import dev.savushkin.scada.mobile.backend.dto.*;
import dev.savushkin.scada.mobile.backend.store.PendingWriteCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Executor для выполнения SCADA/PrintSrv команд в scan cycle.
 * <p>
 * Централизует знание о:
 * <ul>
 *   <li>Формировании запросов (DTO)</li>
 *   <li>Вызове команд через socket</li>
 *   <li>Трансформации pending команд в SetUnitVars запросы</li>
 * </ul>
 * <p>
 * Это упрощает тестирование scan cycle и изолирует его от деталей протокола.
 */
@Service
public class ScadaCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScadaCommandExecutor.class);

    private final QueryAllCommand queryAllCommand;
    private final SetUnitVars setUnitVarsCommand;

    public ScadaCommandExecutor(
            QueryAllCommand queryAllCommand,
            SetUnitVars setUnitVarsCommand
    ) {
        this.queryAllCommand = Objects.requireNonNull(queryAllCommand, "queryAllCommand");
        this.setUnitVarsCommand = Objects.requireNonNull(setUnitVarsCommand, "setUnitVarsCommand");
        log.info("ScadaCommandExecutor initialized");
    }

    /**
     * Выполняет QueryAll и возвращает snapshot состояния PrintSrv.
     *
     * @return полный snapshot состояния всех units
     * @throws Exception если команда не выполнилась (например, проблемы с socket/десериализацией)
     */
    public QueryAllResponseDTO queryAllSnapshot() throws Exception {
        QueryAllRequestDTO request = new QueryAllRequestDTO("Line", "QueryAll");
        log.debug("Executing QueryAll command to PrintSrv");
        QueryAllResponseDTO snapshot = queryAllCommand.execute(request);
        log.debug("Received snapshot from PrintSrv with {} units", snapshot.units().size());
        return snapshot;
    }

    /**
     * Выполняет SetUnitVars для всех pending команд из буфера.
     * <p>
     * Отправляет отдельный SetUnitVars запрос для каждого unit.
     * Согласно PrintSrv API, SetUnitVars работает с одним unit за раз.
     *
     * @param pendingWrites карта pending команд (ключ = unitId)
     * @throws IOException если хотя бы одна команда не выполнилась
     */
    public void executeSetUnitVars(Map<Integer, PendingWriteCommand> pendingWrites) throws IOException {
        if (pendingWrites == null || pendingWrites.isEmpty()) {
            log.debug("No pending writes to execute");
            return;
        }

        log.debug("Executing SetUnitVars for {} unit(s): {}", pendingWrites.size(), pendingWrites.keySet());

        // Отправляем команды для каждого unit последовательно
        for (PendingWriteCommand cmd : pendingWrites.values()) {
            try {
                SetUnitVarsRequestDTO request = buildSetUnitVarsRequest(cmd);
                SetUnitVarsResponseDTO response = setUnitVarsCommand.execute(request);
                log.debug("SetUnitVars executed for unit {}: {}", cmd.unit(), cmd.properties());
            } catch (IOException e) {
                // Логируем ошибку и пробрасываем исключение
                // Все команды в этом цикле будут потеряны
                log.error("Failed to execute SetUnitVars for unit {}: {} - {}",
                        cmd.unit(), e.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }

        log.debug("All {} SetUnitVars command(s) executed successfully", pendingWrites.size());
    }

    /**
     * Преобразует pending команду в SetUnitVarsRequestDTO.
     * <p>
     * Формат согласно PrintSrv API:
     * - Unit: целое число (1-based)
     * - Parameters: содержит command как Integer
     *
     * @param command pending команда
     * @return готовый DTO для отправки в PrintSrv
     * @throws IllegalArgumentException если properties не содержат 'command' или значение не Integer
     */
    private SetUnitVarsRequestDTO buildSetUnitVarsRequest(PendingWriteCommand command) {
        // Извлекаем command value из properties с валидацией
        Object commandObj = command.properties().get("command");
        if (commandObj == null) {
            throw new IllegalArgumentException("Command properties must contain 'command' field");
        }
        if (!(commandObj instanceof Integer)) {
            throw new IllegalArgumentException(
                    "Command value must be Integer, got: " + commandObj.getClass().getSimpleName()
            );
        }

        Integer commandValue = (Integer) commandObj;

        // Создаем ParametersDTO с command value
        ParametersDTO parameters = new ParametersDTO(commandValue);

        return new SetUnitVarsRequestDTO(
                "Line",          // DeviceName
                command.unit(),  // Unit (1-based)
                "SetUnitVars",   // Command
                parameters       // Parameters с command value
        );
    }
}
