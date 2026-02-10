package dev.savushkin.scada.mobile.backend.controllers;

import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.dto.SetUnitVarsResponseDTO;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST контроллер для работы с командами PrintSrv.
 * <p>
 * Предоставляет API endpoints:
 * <ul>
 *   <li>GET /api/v1/commands/queryAll - получение текущего состояния из snapshot</li>
 *   <li>POST /api/v1/commands/setUnitVars - добавление команды в буфер для записи</li>
 * </ul>
 * <p>
 * Архитектура Write-Through Cache:
 * <ul>
 *   <li>POST возвращает HTTP 200 немедленно (команда добавлена в буфер)</li>
 *   <li>GET возвращает snapshot на момент последнего scan cycle</li>
 *   <li>Изменения видны в GET после следующего scan cycle (≤ 5 секунд)</li>
 * </ul>
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
     * обновляется через
     * {@link dev.savushkin.scada.mobile.backend.services.polling.PrintSrvPollingScheduler}
     * с интервалом scan cycle (настраивается через <code>printsrv.polling.fixed-delay-ms</code>).
     * <p>
     * Snapshot содержит актуальное состояние PrintSrv на момент последнего scan cycle.
     * Изменения, сделанные через {@link #setUnitVars(int, int)}, появятся здесь
     * после следующего scan cycle (до 5 секунд задержки).
     *
     * @return ResponseEntity с полным состоянием PrintSrv (все units и их свойства)
     * @throws IllegalStateException если snapshot еще не загружен (приложение только запустилось)
     */
    @GetMapping("/queryAll")
    public ResponseEntity<QueryAllResponseDTO> queryAll() {
        log.info("Received GET /queryAll request");
        QueryAllResponseDTO response = commandsService.queryAll();
        log.info("Returning QueryAll response with {} units", response.units().size());
        return ResponseEntity.ok(response);
    }

    /**
     * Добавляет команду изменения значения в буфер для выполнения в следующем Scan Cycle.
     * <p>
     * Метод возвращает HTTP 200 немедленно (< 50ms), не дожидаясь записи в PrintSrv.
     * Команда будет выполнена в следующем scan cycle (до 5 секунд задержки).
     * <p>
     * Клиент может проверить результат выполнения через GET /queryAll
     * после следующего scan cycle.
     * <p>
     * Архитектурные гарантии:
     * <ul>
     *   <li><b>Fast Response</b>: возврат управления < 50ms</li>
     *   <li><b>Eventual Consistency</b>: изменения видны через ≤ 5 секунд</li>
     *   <li><b>Last-Write-Wins</b>: если для одного unit отправлено несколько команд,
     *       в PrintSrv будет записана только последняя</li>
     * </ul>
     *
     * @param unit  номер unit (1-based, например: 1 = u1, 2 = u2)
     * @param value новое значение команды (целое число)
     * @return ResponseEntity с acknowledgment ответом (НЕ реальное состояние из PrintSrv)
     * @throws IllegalStateException если буфер переполнен (HTTP 503 SERVICE_UNAVAILABLE)
     */
    @PostMapping("/setUnitVars")
    public ResponseEntity<SetUnitVarsResponseDTO> setUnitVars(
            @RequestParam int unit,
            @RequestParam int value
    ) {
        log.info("Received POST /setUnitVars request: unit={}, value={}", unit, value);
        SetUnitVarsResponseDTO response = commandsService.setUnitVars(unit, value);
        log.info("SetUnitVars command accepted for unit={} (will be executed in next scan cycle)", unit);
        return ResponseEntity.ok(response);
    }

    /**
     * Обработчик исключений для переполнения буфера команд.
     * <p>
     * Возвращает HTTP 503 SERVICE_UNAVAILABLE, указывая клиенту,
     * что PrintSrv недоступен длительное время и буфер переполнен.
     *
     * @param e исключение переполнения буфера
     * @return ResponseEntity с HTTP 503 и сообщением об ошибке
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleBufferOverflow(IllegalStateException e) {
        log.error("Buffer overflow: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "SERVICE_UNAVAILABLE",
                        "message", e.getMessage(),
                        "hint", "PrintSrv is unavailable. Please try again later."
                ));
    }
}
