package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.ChangeCommandResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueryStateResponseDTO;
import dev.savushkin.scada.mobile.backend.exception.BufferOverflowException;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvPollingScheduler;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
 *   <li>POST возвращает HTTP 200 быстро (команда добавлена в буфер)</li>
 *   <li>GET возвращает snapshot на момент последнего scan cycle</li>
 *   <li>Изменения видны в GET после следующего scan cycle (eventual consistency, интервал задаётся конфигом)</li>
 * </ul>
 */
@Tag(name = "SCADA Commands", description = "API для работы с командами SCADA системы (чтение состояния и запись команд)")
@RestController
@RequestMapping("api/v1/commands")
@Validated
public class CommandsController {

    private static final Logger log = LoggerFactory.getLogger(CommandsController.class);

    private final CommandsService commandsService;
    private final HealthService healthService;

    /**
     * Конструктор контроллера с внедрением зависимостей.
     *
     * @param commandsService сервис для работы с командами SCADA
     * @param healthService   сервис для проверки состояния приложения
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
     * {@link PrintSrvPollingScheduler}
     * с интервалом scan cycle (настраивается через <code>printsrv.polling.fixed-delay-ms</code>).
     * <p>
     * Snapshot содержит актуальное состояние SCADA на момент последнего scan cycle.
     * Изменения, сделанные через {@link #setUnitVars(int, int)}, появятся здесь
     * после следующего scan cycle (eventual consistency). Интервал scan cycle
     * задаётся конфигурацией <code>printsrv.polling.fixed-delay-ms</code>.
     *
     * @return ResponseEntity с полным состоянием SCADA системы (все units и их свойства)
     * @throws IllegalStateException если snapshot еще не загружен (приложение только запустилось)
     */
    @Operation(
            summary = "Получить состояние SCADA системы",
            description = "Возвращает последний snapshot состояния всех units. " +
                    "Данные обновляются автоматически с периодом, заданным конфигурацией scan cycle " +
                    "(<code>printsrv.polling.fixed-delay-ms</code>, может отличаться по профилям). " +
                    "Изменения после POST /setUnitVars станут видны после следующего scan cycle."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Успешно получен snapshot состояния",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QueryStateResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Snapshot еще не загружен (приложение только запустилось или проблемы с PrintSrv)",
                    content = @Content(mediaType = "application/json")
            )
    })
    @GetMapping("/queryAll")
    public ResponseEntity<QueryStateResponseDTO> queryAll() {
        log.debug("Received GET /queryAll request");
        QueryStateResponseDTO response = commandsService.queryAll();
        log.debug("QueryAll request completed");
        return ResponseEntity.ok(response);
    }

    /**
     * Добавляет команду изменения значения в буфер для выполнения в следующем Scan Cycle.
     * <p>
     * Метод возвращает HTTP 200 быстро, не дожидаясь записи в SCADA/PrintSrv.
     * Команда будет выполнена в следующем scan cycle (eventual consistency).
     * Интервал scan cycle настраивается через {@code printsrv.polling.fixed-delay-ms}.
     * <p>
     * Клиент может проверить результат выполнения через GET /queryAll
     * после следующего scan cycle.
     * <p>
     * Архитектурные гарантии:
     * <ul>
     *   <li><b>Fast Response</b>: подтверждение приёма без ожидания записи в PrintSrv</li>
     *   <li><b>Eventual Consistency</b>: изменения видны после следующего scan cycle (интервал задаётся конфигом)</li>
     *   <li><b>Last-Write-Wins</b>: если для одного unit отправлено несколько команд,
     *       в SCADA будет записана только последняя</li>
     * </ul>
     *
     * @param unit  номер unit (1-based, например: 1 = u1, 2 = u2)
     * @param value новое значение команды (целое число)
     * @return ResponseEntity с acknowledgment ответом (НЕ реальное состояние из SCADA)
     * @throws BufferOverflowException если буфер переполнен (HTTP 503 SERVICE_UNAVAILABLE)
     */
    @Operation(
            summary = "Установить значение команды для unit",
            description = "Добавляет команду SetUnitVars в буфер для выполнения в следующем scan cycle. " +
                    "Возвращает подтверждение приёма немедленно, без ожидания записи в PrintSrv. " +
                    "Реальное выполнение произойдёт в очередном цикле опроса (eventual consistency). " +
                    "Если для одного unit отправлено несколько команд до цикла — применится только последняя (Last-Write-Wins)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Команда принята в буфер (будет выполнена в следующем scan cycle)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChangeCommandResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректные параметры (unit или value меньше 1)",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Буфер переполнен (PrintSrv недоступен длительное время)",
                    content = @Content(mediaType = "application/json")
            )
    })
    @PostMapping("/setUnitVars")
    public ResponseEntity<ChangeCommandResponseDTO> setUnitVars(
            @Parameter(description = "Номер unit (1-based): 1 = u1, 2 = u2, и т.д.", required = true, example = "1")
            @RequestParam @Positive @Min(1) int unit,
            @Parameter(description = "Новое значение команды (целое число >= 1)", required = true, example = "128")
            @RequestParam @Positive @Min(1) int value
    ) {
        log.debug("Received POST /setUnitVars request: unit={}, value={}", unit, value);
        ChangeCommandResponseDTO response = commandsService.setUnitVars(unit, value);
        log.debug("SetUnitVars command accepted for unit={} (will be executed in next scan cycle)", unit);
        return ResponseEntity.ok(response);
    }

    /**
     * Liveness probe: приложение запущено и отвечает на запросы.
     * <p>
     * Не проверяет внешние зависимости (SCADA).
     */
    @Operation(
            summary = "Liveness probe",
            description = "Проверка, что приложение запущено и способно отвечать на запросы. " +
                    "Не проверяет доступность PrintSrv."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Приложение работает",
                    content = @Content(mediaType = "application/json")
            )
    })
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
    @Operation(
            summary = "Readiness probe",
            description = "Проверка готовности приложения к обслуживанию запросов. " +
                    "Возвращает 200 OK только если хотя бы один snapshot уже получен из PrintSrv. " +
                    "При старте приложения или длительной недоступности PrintSrv вернёт 503."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Приложение готово (snapshot загружен)",
                    content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Приложение не готово (snapshot еще не получен или устарел)",
                    content = @Content(mediaType = "application/json")
            )
    })
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
