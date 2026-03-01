package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.UnitsDTO_new;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsDTO_new;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
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
@RequestMapping("api/v1.0.0")
@Validated
public class Controller {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private final CommandsService commandsService;
    private final HealthService healthService;
    private final Clock clock;

    /**
     * Конструктор контроллера с внедрением зависимостей.
     *
     * @param commandsService сервис для работы с командами SCADA
     * @param healthService   сервис для проверки состояния приложения
     */
    @Autowired
    public Controller(CommandsService commandsService, HealthService healthService) {
        this(commandsService, healthService, Clock.systemUTC());
    }

    public Controller(CommandsService commandsService, HealthService healthService, @Nullable Clock clock) {
        this.commandsService = commandsService;
        this.healthService = healthService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        log.info("Controller initialized");
    }

    @GetMapping("/workshops")
    public ResponseEntity<WorkshopsDTO_new> getWorkshops() {
        // TODO: Реализовать GET /api/workshops
        return null;
    }

    @GetMapping("workshops/{id}/units")
    public ResponseEntity<UnitsDTO_new> getUnitsInWorkshops(@PathVariable @NonNull @Positive String id) {
        // TODO: Реализовать GET /api/workshops/{id}/units
        return null;
    }


    /**
     * Liveness probe: приложение запущено и отвечает на запросы.
     * <p>
     * Не проверяет внешние зависимости (SCADA).
     */
    @Operation(summary = "Liveness probe", description = "Проверка, что приложение запущено и способно отвечать на запросы. " + "Не проверяет доступность PrintSrv.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Приложение работает", content = @Content(mediaType = "application/json"))})
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, Object>> live() {
        boolean alive = healthService.isAlive();
        return ResponseEntity.ok(Map.of("status", alive ? "UP" : "DOWN", "timestamp", Instant.now(clock).toString()));
    }

    /**
     * Readiness probe: приложение готово обслуживать запросы по данным.
     * <p>
     * Для текущей архитектуры "готовность" означает, что хотя бы один snapshot уже
     * получен через polling/scan cycle и сохранён в {@code PrintSrvSnapshotStore}.
     */
    @Operation(summary = "Readiness probe", description = "Проверка готовности приложения к обслуживанию запросов. " + "Возвращает 200 OK только если хотя бы один snapshot уже получен из PrintSrv. " + "При старте приложения или длительной недоступности PrintSrv вернёт 503.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Приложение готово (snapshot загружен)", content = @Content(mediaType = "application/json")), @ApiResponse(responseCode = "503", description = "Приложение не готово (snapshot еще не получен или устарел)", content = @Content(mediaType = "application/json"))})
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean ready = healthService.isReady();
        HttpStatus status = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of("status", ready ? "UP" : "DOWN", "timestamp", Instant.now(clock).toString(), "ready", ready));
    }
}
