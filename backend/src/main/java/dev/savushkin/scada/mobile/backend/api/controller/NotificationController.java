package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.LastBatchStateDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationToggleResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationWorkflowResponseDTO;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtPrincipalUtil;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationCreatorType;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import dev.savushkin.scada.mobile.backend.infrastructure.ws.NotificationMessageFactory;
import dev.savushkin.scada.mobile.backend.services.NotificationService;
import dev.savushkin.scada.mobile.backend.services.NotificationService.ToggleResult;
import dev.savushkin.scada.mobile.backend.services.UnitMappingService;
import dev.savushkin.scada.mobile.backend.services.UserProfileService;
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
import java.util.List;

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
    private final UserProfileService userProfileService;
    private final PrintSrvTopologyRepository topologyRepository;

    public NotificationController(NotificationService notificationService,
                                  UnitMappingService unitMappingService,
                                  UserProfileService userProfileService,
                                  PrintSrvTopologyRepository topologyRepository) {
        this.notificationService = notificationService;
        this.unitMappingService = unitMappingService;
        this.userProfileService = userProfileService;
        this.topologyRepository = topologyRepository;
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
                        NotificationToggleResponseDTO.activated(unit.numericIdValue(), actorIdValue, timestamp,
                            activated.notificationId()));
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

    @PostMapping("/notifications/{notificationId}/accept")
    public ResponseEntity<NotificationWorkflowResponseDTO> acceptNotification(
            @PathVariable long notificationId
    ) {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null || JwtPrincipalUtil.isMachineSubject()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(toDto(notificationService.acceptNotification(notificationId, userId)));
    }

    @PostMapping("/notifications/{notificationId}/complete")
    public ResponseEntity<NotificationWorkflowResponseDTO> completeNotification(
            @PathVariable long notificationId
    ) {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null || JwtPrincipalUtil.isMachineSubject()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(toDto(notificationService.completeNotification(notificationId, userId)));
    }

    @PostMapping("/notifications/{notificationId}/cancel")
    public ResponseEntity<NotificationWorkflowResponseDTO> cancelNotification(
            @PathVariable long notificationId
    ) {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null || JwtPrincipalUtil.isMachineSubject()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(toDto(notificationService.cancelNotification(notificationId, userId)));
    }

    @GetMapping("/notifications/sent-history")
    public ResponseEntity<List<NotificationWorkflowResponseDTO>> sentHistory() {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null || JwtPrincipalUtil.isMachineSubject()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(notificationService.getSentHistory(userId).stream()
                .map(this::toDto).toList());
    }

    @GetMapping("/notifications/executor-history")
    public ResponseEntity<List<NotificationWorkflowResponseDTO>> executorHistory() {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null || JwtPrincipalUtil.isMachineSubject()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(notificationService.getExecutorHistory(userId).stream()
                .map(this::toDto).toList());
    }

    @GetMapping("/notifications/my-tasks")
    public ResponseEntity<List<NotificationWorkflowResponseDTO>> myTasks() {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null || JwtPrincipalUtil.isMachineSubject()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(notificationService.getExecutorHistory(userId).stream()
                .filter(notification -> notification.status() == NotificationStatus.IN_PROGRESS)
                .map(this::toDto).toList());
    }

    @GetMapping("/notifications/incoming")
    public ResponseEntity<List<NotificationWorkflowResponseDTO>> incoming() {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null || JwtPrincipalUtil.isMachineSubject()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(notificationService.getIncoming(userId).stream()
                .map(this::toDto).toList());
    }

    /**
     * Маппит доменное уведомление в DTO, обогащая его читаемыми именами
     * аппарата, создателя и исполнителя. Для уведомлений от автомата
     * ({@link NotificationCreatorType#MACHINE}) создатель — «СКАДА».
     */
    private NotificationWorkflowResponseDTO toDto(ProductionNotification notification) {
        String unitName = topologyRepository.findByInstanceId(notification.unitId())
                .map(PrintSrvInstance::displayName)
                .orElse(notification.unitId());
        String creatorName = notification.creatorType() == NotificationCreatorType.MACHINE
                ? NotificationMessageFactory.MACHINE_CREATOR_NAME
                : userProfileService.resolveFullName(notification.creatorId());
        String acceptedByName = userProfileService.resolveFullName(notification.acceptedBy());
        return NotificationWorkflowResponseDTO.from(notification, unitName, creatorName, acceptedByName);
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
