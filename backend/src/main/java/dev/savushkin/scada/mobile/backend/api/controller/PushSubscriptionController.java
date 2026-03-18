package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.RegisterPushSubscriptionRequest;
import dev.savushkin.scada.mobile.backend.application.ports.PushSubscriptionRepository;
import dev.savushkin.scada.mobile.backend.domain.model.PushSubscription;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST-контроллер управления Web Push подписками.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code POST .../push/subscriptions} — регистрация или обновление подписки (upsert)</li>
 *   <li>{@code DELETE .../push/subscriptions} — деактивация подписки</li>
 * </ul>
 * <p>
 * Все запросы проходят Bean Validation. Невалидные запросы возвращают 400 с телом
 * Problem+JSON (обрабатывает {@code GlobalExceptionHandler}).
 */
@Tag(name = "Push Notifications", description = "Управление Web Push подписками устройств")
@RestController
@Validated
@RequestMapping("${scada.api.base-path}/push/subscriptions")
public class PushSubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionController.class);

    private final PushSubscriptionRepository subscriptionRepository;

    public PushSubscriptionController(PushSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Operation(
            summary = "Зарегистрировать push-подписку",
            description = """
                    Upsert push-подписки устройства. Если подписка с данным installationId
                    уже существует — обновляет endpoint и ключи (перевыпуск браузером).
                    Клиент должен вызывать этот endpoint:
                    - при первом предоставлении разрешения на уведомления;
                    - при перезапуске приложения (re-sync подписки);
                    - при срабатывании события pushsubscriptionchange в Service Worker.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Подписка зарегистрирована или обновлена"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос",
                    content = @Content(mediaType = "application/problem+json"))
    })
    @PostMapping
    public ResponseEntity<Void> registerSubscription(
            @RequestBody @Valid @NonNull RegisterPushSubscriptionRequest request
    ) {
        PushSubscription subscription = new PushSubscription(
                request.installationId(),
                request.subscription().endpoint(),
                request.subscription().keys().p256dh(),
                request.subscription().keys().auth(),
                request.platform(),
                request.appChannel(),
                request.preferredWorkshopId(),
                request.preferredUnitId(),
                Instant.now(),
                true
        );
        subscriptionRepository.save(subscription);
        log.info("Push subscription registered: installationId='{}', platform='{}', channel='{}'",
                request.installationId(), request.platform(), request.appChannel());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(
            summary = "Деактивировать push-подписку",
            description = """
                    Деактивирует push-подписку для данного устройства.
                    Вызывается при отзыве разрешения на уведомления или явном отказе от них.
                    Запись остаётся в хранилище с флагом active=false (не удаляется физически).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Подписка деактивирована"),
            @ApiResponse(responseCode = "400", description = "Невалидный запрос",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "404", description = "Подписка не найдена")
    })
    @DeleteMapping
    public ResponseEntity<Void> deactivateSubscription(
            @RequestParam
            @NotBlank(message = "installationId обязателен")
            @Size(max = 128, message = "installationId не должен превышать 128 символов")
            String installationId
    ) {
        boolean deactivated = subscriptionRepository.deactivate(installationId);
        if (!deactivated) {
            return ResponseEntity.notFound().build();
        }
        log.info("Push subscription deactivated: installationId='{}'", installationId);
        return ResponseEntity.noContent().build();
    }
}
