package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.LastBatchStateDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationToggleResponseDTO;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtPrincipalUtil;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import dev.savushkin.scada.mobile.backend.services.NotificationService;
import dev.savushkin.scada.mobile.backend.services.NotificationService.ToggleResult;
import dev.savushkin.scada.mobile.backend.services.UnitMappingService;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * REST-контроллер производственных уведомлений («последняя партия»).
 * <p>
 * Единый API для фронтенда (пользовательский JWT) и СКАДА-систем
 * (machine-JWT с {@code subject_type = "machine"}):
 * <ul>
 *   <li>{@code POST /line/{unitId}/last-batch} — toggle установки/снятия флага;</li>
 *   <li>{@code GET /line/{unitId}/last-batch} — чтение текущего состояния.</li>
 * </ul>
 * Состояние хранится перманентно на сервере (таблица {@code production_notifications}).
 * <p>
 * Автомат (machine-JWT) может управлять и читать состояние только собственного
 * аппарата: {@code sub} токена (PrintSrv instance id) обязан совпадать с аппаратом
 * из пути запроса — иначе 403.
 */
@RestController
@RequestMapping("${scada.api.base-path}")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final UnitMappingService unitMappingService;

    public NotificationController(NotificationService notificationService,
                                  UnitMappingService unitMappingService) {
        this.notificationService = notificationService;
        this.unitMappingService = unitMappingService;
    }

    @PostMapping("/line/{unitId}/last-batch")
    public ResponseEntity<NotificationToggleResponseDTO> toggleNotification(
            @PathVariable @NonNull String unitId
    ) {
        log.info("Request: POST /line/{}/last-batch", unitId);

        ResolvedUnit unit = resolveUnit(unitId);
        if (unit == null) {
            return ResponseEntity.status(404).body(
                    new NotificationToggleResponseDTO("error", unitId, null, null));
        }

        ToggleResult result;
        String actorIdValue;

        if (JwtPrincipalUtil.isMachineSubject()) {
            // Автомат (СКАДА): sub токена обязан соответствовать аппарату из пути
            Jwt jwt = JwtPrincipalUtil.getCurrentJwt();
            String machineId = jwt != null ? jwt.getSubject() : null;
            if (machineId == null || !machineId.equals(unit.printsrvInstanceId())) {
                log.warn("Notification toggle forbidden: machine '{}' requested unit '{}'",
                        machineId, unit.printsrvInstanceId());
                return ResponseEntity.status(403).body(
                        new NotificationToggleResponseDTO("error", unitId, null, null));
            }
            actorIdValue = machineId;
            result = notificationService.toggleMachineNotification(unit.printsrvInstanceId(), machineId);
        } else {
            Long userId = JwtPrincipalUtil.getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(401).body(
                        new NotificationToggleResponseDTO("error", unitId, null, null));
            }
            actorIdValue = Long.toString(userId);
            result = notificationService.toggleNotification(unit.printsrvInstanceId(), userId);
        }

        return switch (result) {
            case ToggleResult.Activated activated -> {
                String timestamp = Instant.now().atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                log.info("Notification toggle: ACTIVATED unitId='{}' printsrv='{}' actor='{}'",
                        unit.numericIdValue(), unit.printsrvInstanceId(), actorIdValue);
                yield ResponseEntity.ok(
                        NotificationToggleResponseDTO.activated(unit.numericIdValue(), actorIdValue, timestamp));
            }
            case ToggleResult.Deactivated deactivated -> {
                log.info("Notification toggle: DEACTIVATED unitId='{}' printsrv='{}' actor='{}'",
                        unit.numericIdValue(), unit.printsrvInstanceId(), actorIdValue);
                yield ResponseEntity.ok(
                        NotificationToggleResponseDTO.deactivated(unit.numericIdValue()));
            }
            case ToggleResult.AlreadyActiveByOther alreadyActive -> {
                log.info("Notification toggle: ALREADY_ACTIVE unitId='{}' printsrv='{}' by='{}', requester='{}'",
                        unit.numericIdValue(), unit.printsrvInstanceId(), alreadyActive.existingCreatorId(), actorIdValue);
                yield ResponseEntity.status(409).body(
                        NotificationToggleResponseDTO.alreadyActive(unit.numericIdValue(), alreadyActive.existingCreatorId()));
            }
        };
    }

    /**
     * Возвращает текущее состояние «последняя партия» по аппарату.
     * <p>
     * Доступно любому аутентифицированному работнику; автомат (machine-JWT)
     * может читать только собственный аппарат.
     */
    @GetMapping("/line/{unitId}/last-batch")
    public ResponseEntity<LastBatchStateDTO> getLastBatchState(
            @PathVariable @NonNull String unitId
    ) {
        log.info("Request: GET /line/{}/last-batch", unitId);

        ResolvedUnit unit = resolveUnit(unitId);
        if (unit == null) {
            return ResponseEntity.notFound().build();
        }

        if (JwtPrincipalUtil.isMachineSubject()) {
            Jwt jwt = JwtPrincipalUtil.getCurrentJwt();
            String machineId = jwt != null ? jwt.getSubject() : null;
            if (machineId == null || !machineId.equals(unit.printsrvInstanceId())) {
                log.warn("Last-batch read forbidden: machine '{}' requested unit '{}'",
                        machineId, unit.printsrvInstanceId());
                return ResponseEntity.status(403).build();
            }
        }

        ProductionNotification active = notificationService
                .getActiveNotification(unit.printsrvInstanceId())
                .orElse(null);

        if (active == null) {
            return ResponseEntity.ok(
                    LastBatchStateDTO.inactive(unit.numericIdValue(), unit.printsrvInstanceId()));
        }

        String activatedAt = active.activatedAt().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ResponseEntity.ok(LastBatchStateDTO.active(
                unit.numericIdValue(),
                unit.printsrvInstanceId(),
                active.creatorType().name(),
                active.creatorId(),
                activatedAt
        ));
    }

    /**
     * Разрешает идентификатор аппарата из пути (числовой unit id или
     * PrintSrv instance id) в оба представления.
     *
     * @return пару идентификаторов или {@code null}, если аппарат не найден
     */
    private @Nullable ResolvedUnit resolveUnit(@NonNull String unitId) {
        Long numericUnitId = parseLong(unitId);
        String printsrvUnitId;

        if (numericUnitId != null) {
            printsrvUnitId = unitMappingService.findPrintSrvInstanceId(numericUnitId).orElse(null);
        } else {
            printsrvUnitId = unitId;
            numericUnitId = unitMappingService.findUnitIdByPrintSrvInstanceId(unitId).orElse(null);
        }

        if (printsrvUnitId == null || printsrvUnitId.isBlank()) {
            return null;
        }
        String numericIdValue = numericUnitId != null ? Long.toString(numericUnitId) : unitId;
        return new ResolvedUnit(numericIdValue, printsrvUnitId);
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Аппарат в двух представлениях идентификатора.
     *
     * @param numericIdValue     числовой unit id (строкой)
     * @param printsrvInstanceId PrintSrv instance id
     */
    private record ResolvedUnit(String numericIdValue, String printsrvInstanceId) {
    }
}
