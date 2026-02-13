package dev.savushkin.scada.mobile.backend.services.polling;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import dev.savushkin.scada.mobile.backend.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.printsrv.client.QueryAllCommand;
import dev.savushkin.scada.mobile.backend.printsrv.client.SetUnitVars;
import dev.savushkin.scada.mobile.backend.printsrv.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Executor для выполнения SCADA команд в scan cycle.
 * <p>
 * Теперь работает с domain моделями и использует mappers для преобразования:
 * <ul>
 *   <li>PrintSrv DTO → Domain Model (через {@link PrintSrvMapper})</li>
 *   <li>Domain Model → PrintSrv DTO (при отправке команд)</li>
 * </ul>
 * <p>
 * Это обеспечивает:
 * <ul>
 *   <li>Изоляцию от протокола PrintSrv</li>
 *   <li>Работу с типобезопасными domain моделями</li>
 *   <li>Упрощение тестирования</li>
 * </ul>
 */
@Service
public class ScadaCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScadaCommandExecutor.class);

    private final QueryAllCommand queryAllCommand;
    private final SetUnitVars setUnitVarsCommand;
    private final PrintSrvMapper printSrvMapper;

    public ScadaCommandExecutor(
            QueryAllCommand queryAllCommand,
            SetUnitVars setUnitVarsCommand,
            PrintSrvMapper printSrvMapper
    ) {
        this.queryAllCommand = queryAllCommand;
        this.setUnitVarsCommand = setUnitVarsCommand;
        this.printSrvMapper = printSrvMapper;
        log.info("ScadaCommandExecutor initialized with domain model support");
    }

    /**
     * Выполняет QueryAll и возвращает domain snapshot состояния SCADA.
     *
     * @return domain модель состояния устройства
     * @throws Exception если команда не выполнилась (например, проблемы с socket/десериализацией)
     */
    public DeviceSnapshot queryAllSnapshot() throws Exception {
        QueryAllRequestDTO request = new QueryAllRequestDTO("Line", "QueryAll");
        log.debug("Executing QueryAll command to PrintSrv");

        // Get PrintSrv DTO response
        QueryAllResponseDTO responseDto = queryAllCommand.execute(request);
        log.debug("Received response from PrintSrv with {} units", responseDto.units().size());

        // Convert to domain model
        DeviceSnapshot snapshot = printSrvMapper.toDomainDeviceSnapshot(responseDto);
        log.debug("Converted to domain snapshot with {} units", snapshot.getUnitCount());

        return snapshot;
    }

    /**
     * Выполняет SetUnitVars для всех pending команд из буфера.
     * <p>
     * Отправляет отдельный SetUnitVars запрос для каждого unit.
     * Согласно PrintSrv API, SetUnitVars работает с одним unit за раз.
     *
     * @param pendingWrites карта pending команд (ключ = unitNumber)
     * @throws IOException если хотя бы одна команда не выполнилась
     */
    public void executeSetUnitVars(Map<Integer, WriteCommand> pendingWrites) throws IOException {
        if (pendingWrites == null || pendingWrites.isEmpty()) {
            log.debug("No pending writes to execute");
            return;
        }

        log.debug("Executing SetUnitVars for {} unit(s): {}", pendingWrites.size(), pendingWrites.keySet());

        // Отправляем команды для каждого unit последовательно
        for (WriteCommand cmd : pendingWrites.values()) {
            try {
                SetUnitVarsRequestDTO request = buildSetUnitVarsRequest(cmd);
                setUnitVarsCommand.execute(request);
                log.debug("SetUnitVars executed for unit {}: command={}", cmd.getUnitNumber(), cmd.getCommandValue());
            } catch (IOException e) {
                // Логируем ошибку и пробрасываем исключение
                // Все команды в этом цикле будут потеряны
                log.error("Failed to execute SetUnitVars for unit {}: {} - {}",
                        cmd.getUnitNumber(), e.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }

        log.debug("All {} SetUnitVars command(s) executed successfully", pendingWrites.size());
    }

    /**
     * Преобразует domain WriteCommand в SetUnitVarsRequestDTO.
     * <p>
     * Формат согласно PrintSrv API:
     * - Unit: целое число (1-based)
     * - Parameters: содержит command как Integer
     *
     * @param command domain write command
     * @return готовый DTO для отправки в PrintSrv
     * @throws IllegalArgumentException если properties не содержат 'command' или значение не Integer
     */
    private SetUnitVarsRequestDTO buildSetUnitVarsRequest(WriteCommand command) {
        int commandValue = command.getCommandValue();

        // Создаём ParametersDTO с command value
        ParametersDTO parameters = new ParametersDTO(commandValue);

        return new SetUnitVarsRequestDTO(
                "Line",                  // DeviceName
                command.getUnitNumber(), // Unit (1-based)
                "SetUnitVars",          // Command
                parameters              // Parameters с command value
        );
    }
}
