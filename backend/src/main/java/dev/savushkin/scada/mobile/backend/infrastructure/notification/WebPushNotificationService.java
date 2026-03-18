package dev.savushkin.scada.mobile.backend.infrastructure.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import dev.savushkin.scada.mobile.backend.application.ports.PushSubscriptionRepository;
import dev.savushkin.scada.mobile.backend.config.VapidProperties;
import dev.savushkin.scada.mobile.backend.domain.model.PushSubscription;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Доставляет Web Push-уведомления устройствам при изменении состояния алёртов.
 * <p>
 * Подписывается на {@link AlertNotificationEvent} через {@code @EventListener} и рассылает
 * уведомления всем активным подпискам, хранящимся в {@link PushSubscriptionRepository}.
 *
 * <h3>Протокол</h3>
 * Использует Web Push Protocol (RFC 8030) + VAPID (RFC 8292) через библиотеку
 * {@code nl.martijndwars:web-push}. Шифрование payload — aesgcm (RFC 8291).
 *
 * <h3>Fail-safe поведение</h3>
 * <ul>
 *   <li>Если VAPID-ключи не заданы — сервис отключается при старте, логирует предупреждение.</li>
 *   <li>Ошибки доставки на конкретный endpoint не прерывают рассылку остальным.</li>
 *   <li>HTTP 410 Gone / 404 Not Found — подписка автоматически деактивируется.</li>
 * </ul>
 *
 * <h3>Формат payload</h3>
 * JSON-объект, который Service Worker разбирает в обработчике события {@code push}:
 * <pre>{@code
 * {
 *   "alertId":  "dess:hassia1:Critical:2026-03-18T10:00:00",
 *   "state":    "ACTIVATED",
 *   "title":    "🔴 Hassia №1 — Авария",
 *   "body":     "CamChecker: ошибка маркировки; Line: останов линии",
 *   "route":    "/workshops/dess/units/hassia1/logs",
 *   "severity": "Critical"
 * }
 * }</pre>
 */
@Component
public class WebPushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebPushNotificationService.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final VapidProperties vapidProperties;
    private final ObjectMapper objectMapper;

    @Nullable
    private PushService pushService;

    public WebPushNotificationService(
            PushSubscriptionRepository subscriptionRepository,
            VapidProperties vapidProperties,
            ObjectMapper objectMapper
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.vapidProperties = vapidProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Инициализирует {@link PushService} с VAPID-ключами из конфигурации.
     * <p>
     * Если ключи не заданы или невалидны — Web Push отключается без выброса исключения
     * (fail-safe: сервис стартует, но уведомления не отправляются).
     */
    @PostConstruct
    public void init() {
        if (!vapidProperties.isConfigured()) {
            log.warn("VAPID keys are not configured — Web Push notifications disabled. "
                    + "Set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY environment variables to enable.");
            return;
        }
        try {
            this.pushService = new PushService()
                    .setPublicKey(vapidProperties.getPublicKey())
                    .setPrivateKey(vapidProperties.getPrivateKey())
                    .setSubject(vapidProperties.getSubject());
            log.info("Web Push service initialized (subject='{}')", vapidProperties.getSubject());
        } catch (Exception e) {
            log.error("Failed to initialize Web Push service — push notifications disabled. "
                    + "Check VAPID key format (must be Base64url EC P-256).", e);
        }
    }

    /**
     * Обрабатывает изменение состояния алёрта и рассылает push-уведомления.
     * <p>
     * Вызывается синхронно из потока опроса PrintSrv. Ошибки доставки логируются,
     * но не прерывают поток обработки событий.
     *
     * @param event событие изменения состояния алёрта
     */
    @EventListener
    public void onAlertNotification(AlertNotificationEvent event) {
        if (pushService == null) {
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findAllActive();
        if (subscriptions.isEmpty()) {
            log.debug("No active push subscriptions — skipping notification for alertId='{}'",
                    event.getAlertId());
            return;
        }

        byte[] payload;
        try {
            payload = buildPayload(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize push payload for alertId='{}'", event.getAlertId(), e);
            return;
        }

        log.debug("Delivering push notification to {} subscription(s): alertId='{}', state='{}'",
                subscriptions.size(), event.getAlertId(), event.getState());

        for (PushSubscription sub : subscriptions) {
            deliverToSubscription(sub, payload, event.getAlertId());
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void deliverToSubscription(PushSubscription sub, byte[] payload, String alertId) {
        try {
            Notification notification = new Notification(
                    sub.endpoint(),
                    sub.p256dhKey(),
                    sub.authKey(),
                    payload
            );
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();

            if (status == HttpStatus.SC_GONE || status == HttpStatus.SC_NOT_FOUND) {
                // Endpoint больше недействителен — деактивируем подписку.
                subscriptionRepository.deactivate(sub.installationId());
                log.info("Push subscription expired (HTTP {}), deactivated: installationId='{}', alertId='{}'",
                        status, sub.installationId(), alertId);
            } else if (status == HttpStatus.SC_CREATED) {
                log.debug("Push notification delivered: installationId='{}', alertId='{}'",
                        sub.installationId(), alertId);
            } else {
                log.warn("Push delivery returned unexpected status {}: installationId='{}', alertId='{}'",
                        status, sub.installationId(), alertId);
            }
        } catch (Exception e) {
            log.error("Push delivery error: installationId='{}', alertId='{}'",
                    sub.installationId(), alertId, e);
        }
    }

    /**
     * Формирует JSON-payload уведомления.
     * <p>
     * Body содержит ошибки, сгруппированные по устройству:
     * {@code "CamChecker: ошибка маркировки; Line: останов линии"}.
     */
    private byte[] buildPayload(AlertNotificationEvent event) throws JsonProcessingException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", event.getAlertId());
        payload.put("state", event.getState().name());
        payload.put("title", buildTitle(event));
        payload.put("body", buildBody(event));
        payload.put("route", event.getRoute());
        payload.put("severity", event.getSeverity());
        return objectMapper.writeValueAsBytes(payload);
    }

    private String buildTitle(AlertNotificationEvent event) {
        String icon = switch (event.getState()) {
            case ACTIVATED -> severityIcon(event.getSeverity());
            case RESOLVED -> "✅";
            default -> "ℹ️";
        };
        String label = switch (event.getState()) {
            case ACTIVATED -> "Авария";
            case RESOLVED -> "Авария устранена";
            default -> "Обновление статуса";
        };
        return icon + " " + event.getUnitName() + " — " + label;
    }

    /**
     * Формирует тело уведомления.
     * <p>
     * Для активного алёрта: ошибки сгруппированы по устройству:
     * {@code "CamChecker: ошибка маркировки; Line: останов линии"}.
     * Для resolved: фиксированный текст о восстановлении.
     */
    private String buildBody(AlertNotificationEvent event) {
        List<AlertErrorDTO> errors = event.getErrors();
        if (event.getState() == AlertNotificationEvent.State.RESOLVED || errors.isEmpty()) {
            return "Аппарат возобновил работу";
        }
        return errors.stream()
                .map(e -> e.device() + ": " + e.message())
                .collect(Collectors.joining("; "));
    }

    private String severityIcon(String severity) {
        return switch (severity.toLowerCase()) {
            case "critical" -> "🔴";
            default -> "⚠️";
        };
    }
}
