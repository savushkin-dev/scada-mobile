package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.UnitsDTO;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsDTO;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
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
import java.util.List;
import java.util.Map;

/**
 * REST контроллер SCADA Mobile API.
 * <p>
 * Предоставляет API endpoints:
 * <ul>
 *   <li>GET ${scada.api.base-path}/workshops — список цехов с актуальной статистикой</li>
 *   <li>GET ${scada.api.base-path}/workshops/{id}/units — список аппаратов цеха с текущим состоянием</li>
 *   <li>GET ${scada.api.base-path}/health/live — liveness probe</li>
 *   <li>GET ${scada.api.base-path}/health/ready — readiness probe</li>
 * </ul>
 */
@Tag(name = "SCADA Mobile", description = "API для мобильного приложения SCADA (цеха, аппараты, health)")
@RestController
@Validated
@RequestMapping("${scada.api.base-path}")
public class Controller {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private final WorkshopService workshopService;
    private final HealthService healthService;
    private final Clock clock;

    @Autowired
    public Controller(WorkshopService workshopService, HealthService healthService) {
        this(workshopService, healthService, Clock.systemUTC());
    }

    public Controller(WorkshopService workshopService, HealthService healthService, @Nullable Clock clock) {
        this.workshopService = workshopService;
        this.healthService = healthService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        log.info("Controller initialized");
    }

    // ─── Workshops / Units API ────────────────────────────────────────────────

    @Operation(summary = "Список цехов", description = "Возвращает все цеха с текущей статистикой problemUnits.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Список цехов"))
    @GetMapping("/workshops")
    public ResponseEntity<List<WorkshopsDTO>> getWorkshops() {
        List<WorkshopsDTO> workshops = workshopService.getWorkshops();
        return ResponseEntity.ok(workshops);
    }

    @Operation(summary = "Аппараты цеха", description = "Возвращает список аппаратов/линий для указанного цеха с текущим событием и таймером.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список аппаратов"),
            @ApiResponse(responseCode = "404", description = "Цех не найден")
    })
    @GetMapping("/workshops/{id}/units")
    public ResponseEntity<List<UnitsDTO>> getUnitsInWorkshop(@PathVariable @NonNull String id) {
        if (!workshopService.workshopExists(id)) {
            return ResponseEntity.notFound().build();
        }
        List<UnitsDTO> units = workshopService.getUnits(id);
        return ResponseEntity.ok(units);
    }

    // ─── Health probes ────────────────────────────────────────────────────────

    @Operation(summary = "Liveness probe", description = "Проверка, что приложение запущено.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Приложение работает",
            content = @Content(mediaType = "application/json")))
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, Object>> live() {
        boolean alive = healthService.isAlive();
        return ResponseEntity.ok(Map.of(
                "status", alive ? "UP" : "DOWN",
                "timestamp", Instant.now(clock).toString()));
    }

    @Operation(summary = "Readiness probe", description = "Проверка готовности: хотя бы один snapshot получен.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Готово",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "503", description = "Не готово",
                    content = @Content(mediaType = "application/json"))
    })
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean ready = healthService.isReady();
        HttpStatus status = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.of(
                "status", ready ? "UP" : "DOWN",
                "timestamp", Instant.now(clock).toString(),
                "ready", ready));
    }
}
