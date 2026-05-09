package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.NotificationToggleResponseDTO;
import dev.savushkin.scada.mobile.backend.config.UserIdFilter;
import dev.savushkin.scada.mobile.backend.services.NotificationService;
import dev.savushkin.scada.mobile.backend.services.NotificationService.ToggleResult;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * REST-контроллер для производственных уведомлений.
 * <p>
 * Реализует toggle-эндпоинт {@code POST /api/line/{unitId}/last-batch},
 * который уже вызывается фронтендом (FAB «Последняя партия»).
 *
 * <h3>Протокол</h3>
 * <ul>
 *   <li>Заголовок {@code X-User-Id} — идентификатор работника (обязателен).</li>
 *   <li>Тело запроса: пустое (игнорируется) или JSON.</li>
 *   <li>Ответ: {@link NotificationToggleResponseDTO} — результат toggle.</li>
 * </ul>
 *
 * <h3>Коды ответа</h3>
 * <ul>
 *   <li>200 — success (activated / deactivated).</li>
 *   <li>400 — отсутствует заголовок X-User-Id.</li>
 *   <li>403 — работник не имеет доступа к аппарату.</li>
 *   <li>409 — уведомление уже активно другим работником.</li>
 * </ul>
 */
@RestController
@RequestMapping("${scada.api.base-path}")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Toggle производственного уведомления для аппарата.
     *
     * @param unitId  Идентификатор аппарата из пути URL.
     * @param request HTTP-запрос (для извлечения X-User-Id).
     * @return Результат toggle: activated / deactivated / already_active.
     */
    @PostMapping("/line/{unitId}/last-batch")
    public ResponseEntity<NotificationToggleResponseDTO> toggleNotification(
            @PathVariable @NonNull String unitId,
            @NonNull HttpServletRequest request
    ) {
        String userId = UserIdFilter.resolveUserId(request);
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new NotificationToggleResponseDTO("error", unitId, null, null));
        }

        ToggleResult result = notificationService.toggleNotification(unitId, userId);

        return switch (result) {
            case ToggleResult.Activated activated -> {
                String timestamp = Instant.now().atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                log.info("Notification toggle: ACTIVATED unitId='{}' userId='{}'", unitId, userId);
                yield ResponseEntity.ok(
                        NotificationToggleResponseDTO.activated(unitId, userId, timestamp));
            }
            case ToggleResult.Deactivated deactivated -> {
                log.info("Notification toggle: DEACTIVATED unitId='{}' userId='{}'", unitId, userId);
                yield ResponseEntity.ok(
                        NotificationToggleResponseDTO.deactivated(unitId));
            }
            case ToggleResult.AlreadyActiveByOther alreadyActive -> {
                log.info("Notification toggle: ALREADY_ACTIVE unitId='{}' by='{}', requester='{}'",
                        unitId, alreadyActive.existingCreatorId(), userId);
                yield ResponseEntity.status(409).body(
                        NotificationToggleResponseDTO.alreadyActive(unitId, alreadyActive.existingCreatorId()));
            }
        };
    }
}
