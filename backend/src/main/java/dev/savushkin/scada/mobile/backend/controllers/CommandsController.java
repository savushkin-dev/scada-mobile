package dev.savushkin.scada.mobile.backend.controllers;

import dev.savushkin.scada.mobile.backend.api.dto.ChangeCommandResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueryStateResponseDTO;
import dev.savushkin.scada.mobile.backend.exception.BufferOverflowException;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST контроллер для работы с командами SCADA системы.
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
    private final HealthService healthService;

    /**
     * Конструктор контроллера с внедрением зависимостей.
     *
     * @param commandsService сервис для работы с командами SCADA
     * @param healthService    сервис для проверки состояния приложения
     */
    public CommandsController(CommandsService commandsService, HealthService healthService) {
        this.commandsService = commandsService;
        this.healthService = healthService;
        log.info("CommandsController initialized");
    }

    /**
     * Получает текущий snapshot состояния SCADA системы.
     * <p>
     * Данные берутся из in-memory хранилища, которое автоматически
     * обновляется через
     * {@link dev.savushkin.scada.mobile.backend.services.polling.PrintSrvPollingScheduler}
     * с интервалом scan cycle (настраивается через <code>printsrv.polling.fixed-delay-ms</code>).
     * <p>
     * Snapshot содержит актуальное состояние SCADA на момент последнего scan cycle.
     * Изменения, сделанные через {@link #setUnitVars(int, int)}, появятся здесь
     * после следующего scan cycle (до 5 секунд задержки).
     *
     * @return ResponseEntity с полным состоянием SCADA системы (все units и их свойства)
     * @throws IllegalStateException если snapshot еще не загружен (приложение только запустилось)
     */
    @GetMapping("/queryAll")
    public ResponseEntity<QueryStateResponseDTO> queryAll() {
        log.info("Received GET /queryAll request");
        QueryStateResponseDTO response = commandsService.queryAll();
        log.info("Returning QueryAll response");
        return ResponseEntity.ok(response);
    }

    /**
     * Добавляет команду изменения значения в буфер для выполнения в следующем Scan Cycle.
     * <p>
     * Метод возвращает HTTP 200 немедленно (< 50ms), не дожидаясь записи в SCADA.
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
     *       в SCADA будет записана только последняя</li>
     * </ul>
     *
     * @param unit  номер unit (1-based, например: 1 = u1, 2 = u2)
     * @param value новое значение команды (целое число)
     * @return ResponseEntity с acknowledgment ответом (НЕ реальное состояние из SCADA)
     * @throws BufferOverflowException если буфер переполнен (HTTP 503 SERVICE_UNAVAILABLE)
     */
    @PostMapping("/setUnitVars")
    public ResponseEntity<ChangeCommandResponseDTO> setUnitVars(
            @RequestParam int unit,
            @RequestParam int value
    ) {
        log.info("Received POST /setUnitVars request: unit={}, value={}", unit, value);
        ChangeCommandResponseDTO response = commandsService.setUnitVars(unit, value);
        log.info("SetUnitVars command accepted for unit={} (will be executed in next scan cycle)", unit);
        return ResponseEntity.ok(response);
    }

    /**
     * Liveness probe: приложение запущено и отвечает на запросы.
     * <p>
     * Не проверяет внешние зависимости (SCADA).
     */
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, Object>> live() {
        boolean alive = healthService.isAlive();
        return ResponseEntity.ok(Map.of(
                "status", alive ? "UP" : "DOWN",
                "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Readiness probe: приложение готово обслуживать запросы по данным.
     * <p>
     * Для текущей архитектуры "готовность" означает, что хотя бы один snapshot уже
     * получен через polling/scan cycle и сохранён в {@code PrintSrvSnapshotStore}.
     */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean ready = healthService.isReady();
        HttpStatus status = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of(
                "status", ready ? "UP" : "DOWN",
                "timestamp", Instant.now().toString(),
                "ready", ready
        ));
    }
}
