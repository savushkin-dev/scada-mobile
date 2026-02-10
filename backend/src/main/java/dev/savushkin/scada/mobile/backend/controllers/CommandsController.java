package dev.savushkin.scada.mobile.backend.controllers;

import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsResponseDTO;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * REST контроллер для работы с командами PrintSrv.
 * <p>
 * Предоставляет API endpoints:
 * <ul>
 *   <li>GET /api/v1/commands/queryAll - получение текущего состояния из snapshot</li>
 *   <li>POST /api/v1/commands/setUnitVars - изменение значений в PrintSrv</li>
 * </ul>
 * <p>
 * Все данные логируются для отладки и мониторинга.
 */
@RestController
@RequestMapping("api/v1/commands")
public class CommandsController {

    private static final Logger log = LoggerFactory.getLogger(CommandsController.class);

    private final CommandsService commandsService;

    /**
     * Конструктор контроллера с внедрением зависимостей.
     *
     * @param commandsService сервис для работы с командами PrintSrv
     */
    public CommandsController(CommandsService commandsService) {
        this.commandsService = commandsService;
        log.info("CommandsController initialized");
    }

    /**
     * Получает текущий snapshot состояния PrintSrv.
     * <p>
     * Данные берутся из in-memory хранилища, которое автоматически
     * обновляется через {@link dev.savushkin.scada.mobile.backend.services.polling.PrintSrvPollingScheduler}
     * с интервалом, заданным в конфигурации (<code>printsrv.polling.fixed-delay-ms</code>).
     *
     * @return ResponseEntity с полным состоянием PrintSrv (все units и их свойства)
     * @throws IllegalStateException если snapshot еще не загружен (приложение только запустилось)
     */
    @GetMapping("/queryAll")
    public ResponseEntity<QueryAllResponseDTO> queryAll() {
        log.info("Received GET /queryAll request");
        // Получаем snapshot из store (данные обновляются автоматически)
        QueryAllResponseDTO response = commandsService.queryAll();
        log.info("Returning QueryAll response with {} units", response.units().size());
        return ResponseEntity.ok(response);
    }

    /**
     * Изменяет значение команды для указанного unit в PrintSrv.
     * <p>
     * Команда выполняется синхронно через socket-соединение.
     * Обновленное состояние появится в snapshot при следующем опросе
     * (интервал задаётся в конфигурации: <code>printsrv.polling.fixed-delay-ms</code>).
     *
     * @param unit  номер unit (1-based, например: 1 = u1, 2 = u2)
     * @param value новое значение команды (целое число)
     * @return ResponseEntity с частичным ответом (только измененные поля)
     * @throws IOException если произошла ошибка связи с PrintSrv
     */
    @PostMapping("/setUnitVars")
    public ResponseEntity<SetUnitVarsResponseDTO> setUnitVars(
            @RequestParam int unit,
            @RequestParam int value
    ) throws IOException {
        log.info("Received POST /setUnitVars request: unit={}, value={}", unit, value);
        // Выполняем команду изменения значения
        SetUnitVarsResponseDTO response = commandsService.setUnitVars(unit, value);
        log.info("SetUnitVars completed successfully for unit={}", unit);
        return ResponseEntity.ok(response);
    }
}
