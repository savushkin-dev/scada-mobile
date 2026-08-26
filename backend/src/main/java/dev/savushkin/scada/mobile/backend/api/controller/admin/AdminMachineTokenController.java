package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.api.dto.MachineTokenIssueRequestDTO;
import dev.savushkin.scada.mobile.backend.api.dto.MachineTokenIssueResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.MachineTokenViewDTO;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtPrincipalUtil;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.MachineTokenEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.services.MachineTokenService;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Админ-контроллер управления machine-токенами (автоматы / СКАДА).
 * <p>
 * Machine-токен — долгоживущий JWT с {@code subject_type = "machine"},
 * который СКАДА-система автомата использует для доступа к единому API
 * состояния «последняя партия» (установка / снятие / чтение) и к WebSocket-каналу.
 * <p>
 * Значение токена возвращается один раз при выпуске; далее в реестре доступны
 * только метаданные. Отозванный токен отклоняется с 401 на каждом запросе.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/machine-tokens")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMachineTokenController {

    private static final Logger log = LoggerFactory.getLogger(AdminMachineTokenController.class);

    private final MachineTokenService machineTokenService;
    private final UnitJpaRepository unitRepository;

    public AdminMachineTokenController(MachineTokenService machineTokenService,
                                       UnitJpaRepository unitRepository) {
        this.machineTokenService = machineTokenService;
        this.unitRepository = unitRepository;
    }

    /**
     * Выпускает новый machine-токен для аппарата.
     */
    @PostMapping
    public ResponseEntity<MachineTokenIssueResponseDTO> issue(@Valid @RequestBody MachineTokenIssueRequestDTO request) {
        log.info("Request: POST /admin/machine-tokens, unitId={}", request.unitId());
        Long adminId = JwtPrincipalUtil.getCurrentUserId();
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Требуется аутентификация");
        }

        MachineTokenService.IssuedMachineToken issued = machineTokenService
                .issueToken(request.unitId(), adminId, request.ttlDays())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Аппарат не найден или не имеет PrintSrv instance id"));

        return ResponseEntity.status(HttpStatus.CREATED).body(new MachineTokenIssueResponseDTO(
                issued.token(),
                issued.jti(),
                issued.unitId(),
                issued.printsrvInstanceId(),
                format(issued.expiresAt().atOffset(ZoneOffset.UTC).toLocalDateTime())
        ));
    }

    /**
     * Возвращает реестр выданных machine-токенов (без значений токенов).
     */
    @GetMapping
    public ResponseEntity<List<MachineTokenViewDTO>> list() {
        log.info("Request: GET /admin/machine-tokens");
        List<MachineTokenViewDTO> tokens = machineTokenService.listTokens().stream()
                .map(this::toView)
                .toList();
        return ResponseEntity.ok(tokens);
    }

    /**
     * Отзывает machine-токен по {@code jti}. Дальнейшие запросы с ним получают 401.
     */
    @DeleteMapping("/{jti}")
    public ResponseEntity<Void> revoke(@PathVariable @NonNull String jti) {
        log.info("Request: DELETE /admin/machine-tokens/{}", jti);
        if (!machineTokenService.revokeToken(jti)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine-токен не найден");
        }
        return ResponseEntity.noContent().build();
    }

    private MachineTokenViewDTO toView(MachineTokenEntity token) {
        String printsrvInstanceId = unitRepository.findPrintsrvInstanceIdById(token.getUnitId()).orElse(null);
        boolean active = token.getRevokedAt() == null
                && token.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC));
        return new MachineTokenViewDTO(
                token.getJti(),
                token.getUnitId(),
                printsrvInstanceId,
                token.getIssuedBy(),
                format(token.getIssuedAt()),
                format(token.getExpiresAt()),
                token.getRevokedAt() == null ? null : format(token.getRevokedAt()),
                active
        );
    }

    private static String format(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
