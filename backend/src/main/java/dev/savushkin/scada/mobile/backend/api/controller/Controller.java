package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.UnitTopologyDTO;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopTopologyDTO;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST контроллер SCADA Mobile API.
 * <p>
 * Предоставляет API endpoints:
 * <ul>
 *   <li>GET .../workshops/topology — статическая топология цехов (кэшируется по ETag)</li>
 *   <li>GET .../workshops/{id}/units/topology — статическая топология аппаратов цеха</li>
 *   <li>GET .../health/live — liveness probe</li>
 *   <li>GET .../health/ready — readiness probe</li>
 * </ul>
 * <p>
 * Live-статус (problemUnits, event, timer) поставляется по WebSocket:
 * {@code /ws/workshops/status} и {@code /ws/workshops/{id}/units/status}.
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

    // ─── Topology endpoints (статика, ETag-кэшируемые) ───────────────────────

    /**
     * Формирует HTTP-заголовки для topology-ответов с ETag.
     * <p>
     * Формат ETag: {@code "hex-string"} (в кавычках — по RFC 7232).
     *
     * @param etag hex-строка SHA-256 без кавычек
     */
    private static @NonNull HttpHeaders etagHeaders(String etag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"" + etag + "\"");
        return headers;
    }

    /**
     * Проверяет, совпадает ли клиентский ETag из заголовка {@code If-None-Match}
     * с актуальным ETag конфигурации.
     * <p>
     * Поддерживаемые форматы клиентского значения:
     * <ul>
     *   <li>{@code *} — совпадает с любым ETag (RFC 7232 §3.2)</li>
     *   <li>{@code "abc123"} — сильный ETag в кавычках</li>
     *   <li>{@code W/"abc123"} — слабый ETag; для GET используется слабое сравнение</li>
     * </ul>
     * Возвращает {@code true}, если клиент может использовать закэшированный ответ.
     *
     * @param ifNoneMatch значение заголовка {@code If-None-Match} (может быть null)
     * @param serverETag  актуальный ETag без кавычек
     */
    private static boolean isNotModified(@Nullable String ifNoneMatch, @NonNull String serverETag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String normalized = ifNoneMatch.trim();
        if ("*".equals(normalized)) {
            return true;
        }
        // Слабые валидаторы: W/"etag" → снимаем W/ (RFC 7232 §2.3)
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2);
        }
        // Снимаем кавычки
        if (normalized.length() >= 2
                && normalized.charAt(0) == '"'
                && normalized.charAt(normalized.length() - 1) == '"') {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return serverETag.equals(normalized);
    }

    @Operation(
            summary = "Топология цехов",
            description = "Возвращает статическую топологию всех цехов: id, name, totalUnits. " +
                    "Данные меняются только при изменении конфигурации. " +
                    "Ответ содержит заголовок ETag — клиент может кэшировать бессрочно. " +
                    "Поддерживает If-None-Match: при совпадении ETag возвращает 304 без тела. " +
                    "Live-статус (problemUnits) доступен по WebSocket /ws/workshops/status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Топология цехов"),
            @ApiResponse(responseCode = "304", description = "Топология не изменилась (ETag совпал)")
    })
    @GetMapping("/workshops/topology")
    public ResponseEntity<List<WorkshopTopologyDTO>> getWorkshopsTopology(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        String etag = workshopService.getConfigETag();
        if (isNotModified(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(etagHeaders(etag))
                    .build();
        }
        return ResponseEntity.ok()
                .headers(etagHeaders(etag))
                .body(workshopService.getWorkshopsTopology());
    }

    // ─── Health probes ────────────────────────────────────────────────────────

    @Operation(
            summary = "Топология аппаратов цеха",
            description = "Возвращает статическую топологию аппаратов/линий цеха: id, workshopId, unit. " +
                    "Данные меняются только при изменении конфигурации. " +
                    "Ответ содержит заголовок ETag. " +
                    "Поддерживает If-None-Match: при совпадении ETag возвращает 304 без тела. " +
                    "Live-статус (event, timer) доступен по WebSocket /ws/workshops/{id}/units/status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Топология аппаратов"),
            @ApiResponse(responseCode = "304", description = "Топология не изменилась (ETag совпал)"),
            @ApiResponse(responseCode = "404", description = "Цех не найден")
    })
    @GetMapping("/workshops/{id}/units/topology")
    public ResponseEntity<List<UnitTopologyDTO>> getUnitsTopology(
            @PathVariable @NonNull String id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        if (!workshopService.workshopExists(id)) {
            return ResponseEntity.notFound().build();
        }
        String etag = workshopService.getConfigETag();
        if (isNotModified(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(etagHeaders(etag))
                    .build();
        }
        return ResponseEntity.ok()
                .headers(etagHeaders(etag))
                .body(workshopService.getUnitsTopology(id));
    }

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

    // ─── Вспомогательные методы ───────────────────────────────────────────────

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
