package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.api.dto.AlertSnapshotMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationSnapshotMessageDTO;
import dev.savushkin.scada.mobile.backend.config.WebSocketUserIdInterceptor;
import dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveAlertStore;
import dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveNotificationStore;
import dev.savushkin.scada.mobile.backend.services.NotificationService;
import dev.savushkin.scada.mobile.backend.services.NotificationSettingsService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Единственный WebSocket-хендлер приложения — канал {@code /ws/live}.
 * <p>
 * Заменяет три отдельных хендлера (workshops/status, workshops/{id}/units/status, alerts)
 * одним мультиплексированным соединением. Клиент открывает одно соединение при старте
 * и управляет подпиской на конкретный цех через JSON-сообщения (клиент → сервер).
 *
 * <h3>Протокол клиент → сервер</h3>
 * <pre>
 * { "action": "SUBSCRIBE_WORKSHOP",   "workshopId": "apparatniy" }
 * { "action": "UNSUBSCRIBE_WORKSHOP", "workshopId": "apparatniy" }
 * </pre>
 *
 * <h3>Протокол сервер → клиент</h3>
 * <ul>
 *   <li>{@code ALERT_SNAPSHOT} — отправляется <b>один раз</b> сразу после установки
 *       соединения; содержит все активные алёрты на текущий момент.</li>
 *   <li>{@code UNITS_STATUS} — рассылается подписчикам конкретного цеха по мере
 *       готовности отдельных аппаратов.</li>
 *   <li>{@code ALERT} — рассылается <b>всем</b> подключённым клиентам при изменении
 *       набора активных ошибок (дельта: появилась / исчезла).</li>
 * </ul>
 *
 * <h3>Жизненный цикл подписки</h3>
 * <p>Каждая сессия держит в атрибуте {@code "subscribedWorkshop"} идентификатор текущего
 * цеха (или {@code null}). При смене экрана клиент отправляет UNSUBSCRIBE для старого цеха,
 * затем SUBSCRIBE для нового. Реконнект не требует повторной подписки — при переподключении
 * клиент сам отправляет SUBSCRIBE после получения ALERT_SNAPSHOT.
 *
 * <h3>Потокобезопасность</h3>
 * <ul>
 *   <li>{@code allSessions} — {@link CopyOnWriteArraySet}: безопасная итерация при рассылке
 *       параллельно с добавлением/удалением при подключении/отключении.</li>
 *   <li>{@code sessionsByWorkshop} — {@link ConcurrentHashMap} of {@link CopyOnWriteArraySet}:
 *       аналогично для групп по цехам.</li>
 * </ul>
 */
@Component
public class LiveWsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveWsHandler.class);
    private static final String ATTR_SUBSCRIBED_WORKSHOP = "subscribedWorkshop";

    private final ActiveAlertStore alertStore;
    private final ActiveNotificationStore notificationStore;
    private final WorkshopService workshopService;
    private final NotificationService notificationService;
    private final NotificationSettingsService notificationSettingsService;
    private final ObjectMapper objectMapper;

    /**
     * Все активные сессии — для рассылки ALERT и ALERT_SNAPSHOT
     */
    private final Set<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();

    /**
     * workshopId → активные сессии — для адресной рассылки UNITS_STATUS
     */
    private final Map<String, Set<WebSocketSession>> sessionsByWorkshop = new ConcurrentHashMap<>();

    public LiveWsHandler(
            ActiveAlertStore alertStore,
            ActiveNotificationStore notificationStore,
            WorkshopService workshopService,
            NotificationService notificationService,
            NotificationSettingsService notificationSettingsService,
            ObjectMapper objectMapper
    ) {
        this.alertStore = alertStore;
        this.notificationStore = notificationStore;
        this.workshopService = workshopService;
        this.notificationService = notificationService;
        this.notificationSettingsService = notificationSettingsService;
        this.objectMapper = objectMapper;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        allSessions.add(session);
        sendAlertSnapshot(session);
        sendNotificationSnapshot(session);
        log.debug("WS /live: connected, id={}, total={}", session.getId(), allSessions.size());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        removeSession(session);
        log.debug("WS /live: disconnected, id={}, reason={}, remaining={}",
                session.getId(), status, allSessions.size());
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.warn("WS /live: transport error, id={}: {}", session.getId(), exception.getMessage());
        removeSession(session);
    }

    // ─── Incoming actions (client → server) ───────────────────────────────────

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String action = node.path("action").asText(null);
            if (action == null) {
                log.warn("WS /live: missing 'action' field, id={}", session.getId());
                return;
            }

            switch (action) {
                case "SUBSCRIBE_WORKSHOP" -> handleSubscribeWorkshop(session, node);
                case "UNSUBSCRIBE_WORKSHOP" -> handleUnsubscribeWorkshop(session);
                default -> log.warn("WS /live: unknown action='{}', id={}", action, session.getId());
            }
        } catch (IOException e) {
            log.warn("WS /live: failed to parse message, id={}: {}", session.getId(), e.getMessage());
        }
    }

    private void handleSubscribeWorkshop(WebSocketSession session, @NonNull JsonNode node) {
        // Сначала отписываемся от предыдущего цеха (если был)
        handleUnsubscribeWorkshop(session);

        String workshopId = node.path("workshopId").asText(null);
        if (workshopId == null || workshopId.isBlank()) {
            log.warn("WS /live: SUBSCRIBE_WORKSHOP missing workshopId, id={}", session.getId());
            return;
        }

        sessionsByWorkshop
                .computeIfAbsent(workshopId, k -> new CopyOnWriteArraySet<>())
                .add(session);
        session.getAttributes().put(ATTR_SUBSCRIBED_WORKSHOP, workshopId);

        // Отправляем текущий snapshot сразу после подписки, чтобы клиент не ждал
        // следующего polling-обновления и не показывал временно «Нет данных».
        sendUnitsStatusSnapshot(session, workshopId);

        log.debug("WS /live: subscribed workshop={}, id={}", workshopId, session.getId());
    }

    private void handleUnsubscribeWorkshop(@NonNull WebSocketSession session) {
        String prev = (String) session.getAttributes().remove(ATTR_SUBSCRIBED_WORKSHOP);
        if (prev == null) return;

        Set<WebSocketSession> set = sessionsByWorkshop.get(prev);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) sessionsByWorkshop.remove(prev);
        }
        log.debug("WS /live: unsubscribed workshop={}, id={}", prev, session.getId());
    }

    // ─── Outgoing broadcasts (server → client) ───────────────────────────────

    /**
     * Рассылает {@code UNITS_STATUS} всем клиентам, подписанным на данный цех.
     *
     * @param workshopId ID цеха
     * @param json       сериализованный {@link dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO}
     */
    public void broadcastToWorkshop(String workshopId, String json) {
        Set<WebSocketSession> sessions = sessionsByWorkshop.get(workshopId);
        if (sessions == null || sessions.isEmpty()) return;
        sendToSessions(sessions, json);
    }

    /**
     * Рассылает {@code ALERT} всем подключённым клиентам.
     *
     * @param json сериализованный {@link dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO}
     */
    public void broadcastAlert(String json) {
        if (allSessions.isEmpty()) return;
        sendToSessions(allSessions, json);
    }

    /**
     * Рассылает {@code NOTIFICATION} всем подключённым клиентам.
     * <p>
     * Аналог {@link #broadcastAlert(String)} — используется {@code StatusBroadcaster}
     * для немедленной рассылки при toggle-событии.
     *
     * @param json сериализованный {@link dev.savushkin.scada.mobile.backend.api.dto.NotificationMessageDTO}
     */
    public void broadcastNotification(String json) {
        if (allSessions.isEmpty()) return;
        sendToSessions(allSessions, json);
    }

    // ─── Diagnostics ─────────────────────────────────────────────────────────

    /**
     * Идентификаторы цехов, у которых есть хотя бы один активный подписчик.
     * Используется {@code StatusBroadcaster} для адресной рассылки {@code UNITS_STATUS}.
     */
    public Set<String> getSubscribedWorkshopIds() {
        return sessionsByWorkshop.keySet();
    }

    /**
     * Число подключённых клиентов — для метрик/решения о рассылке алёртов.
     */
    public int getTotalSessionCount() {
        return allSessions.size();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Отправляет текущий снапшот активных алёртов новому клиенту.
     * Ошибки сериализации/отправки логируются — соединение при этом не закрывается.
     */
    private void sendAlertSnapshot(WebSocketSession session) {
        try {
            var snapshotMsg = AlertSnapshotMessageDTO.of(alertStore.getAll());
            sendMessageSafely(session, objectMapper.writeValueAsString(snapshotMsg));
            log.debug("WS /live: sent ALERT_SNAPSHOT, alerts={}, id={}",
                    snapshotMsg.payload().size(), session.getId());
        } catch (Exception e) {
            log.warn("WS /live: failed to send ALERT_SNAPSHOT, id={}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Отправляет подписавшейся сессии моментальный {@code UNITS_STATUS},
     * вычисленный из текущего snapshot store.
     *
     * <p>Это устраняет окно до следующего polling-обновления: после SUBSCRIBE_WORKSHOP
     * клиент сразу получает актуальный статус аппаратов из памяти сервера.
     */
    private void sendUnitsStatusSnapshot(WebSocketSession session, String workshopId) {
        try {
            var status = workshopService.getUnitsStatus(workshopId);
            var message = UnitsStatusMessageDTO.of(workshopId, status);
            if (session.isOpen()) {
                sendMessageSafely(session, objectMapper.writeValueAsString(message));
            }
            log.debug("WS /live: sent initial UNITS_STATUS, workshop={}, units={}, id={}",
                    workshopId, status.size(), session.getId());
        } catch (Exception e) {
            log.warn("WS /live: failed to send initial UNITS_STATUS, workshop={}, id={}: {}",
                    workshopId, session.getId(), e.getMessage());
        }
    }

    /**
     * Отправляет снимок всех активных производственных уведомлений новому клиенту.
     * Вызывается сразу после {@code ALERT_SNAPSHOT} при установке соединения.
     */
    private void sendNotificationSnapshot(WebSocketSession session) {
        try {
            Set<String> allowedUnitIds = resolveAllowedNotificationUnits(session);
            List<NotificationMessageDTO> allNotifications = notificationStore.getAll();
            List<NotificationMessageDTO> filtered = allowedUnitIds.isEmpty()
                    ? List.of()
                    : allNotifications.stream()
                        .filter(n -> allowedUnitIds.contains(n.unitId()))
                        .toList();
            var snapshotMsg = NotificationSnapshotMessageDTO.of(filtered);
            sendMessageSafely(session, objectMapper.writeValueAsString(snapshotMsg));
            log.debug("WS /live: sent NOTIFICATION_SNAPSHOT, notifications={}, id={}",
                    snapshotMsg.payload().size(), session.getId());
        } catch (Exception e) {
            log.warn("WS /live: failed to send NOTIFICATION_SNAPSHOT, id={}: {}",
                    session.getId(), e.getMessage());
        }
    }

    private Set<String> resolveAllowedNotificationUnits(WebSocketSession session) {
        OptionalLong userId = resolveUserId(session);
        if (userId.isEmpty()) {
            log.debug("WS /live: missing userId for NOTIFICATION_SNAPSHOT, id={}", session.getId());
            return Set.of();
        }

        long numericUserId = userId.getAsLong();
        Set<String> assigned = notificationService.getSubscribedUnitIds(numericUserId);
        if (assigned.isEmpty()) {
            return Set.of();
        }

        Set<String> enabled = notificationSettingsService.getActivePrintSrvUnitIds(numericUserId);
        if (enabled.isEmpty()) {
            return Set.of();
        }

        Set<String> allowed = new HashSet<>(assigned);
        allowed.retainAll(enabled);
        return allowed;
    }

    private OptionalLong resolveUserId(WebSocketSession session) {
        Object raw = session.getAttributes().get(WebSocketUserIdInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return OptionalLong.of(number.longValue());
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return OptionalLong.of(Long.parseLong(text.trim()));
            } catch (NumberFormatException ex) {
                log.debug("WS /live: invalid userId '{}', id={}", text, session.getId());
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Отправляет JSON-сообщение набору сессий.
     * Закрытые/недоступные сессии удаляются из набора.
     */
    private void sendToSessions(Set<WebSocketSession> sessions, String json) {
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                sendMessageSafely(session, json);
            } catch (IOException | IllegalStateException e) {
                log.warn("WS /live: send failed, id={}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    private void sendMessageSafely(WebSocketSession session, String json) throws IOException {
        synchronized (session) {
            if (!session.isOpen()) {
                throw new IOException("WebSocket session is closed");
            }
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * Полная зачистка сессии из всех структур
     */
    private void removeSession(WebSocketSession session) {
        allSessions.remove(session);
        handleUnsubscribeWorkshop(session);
    }

    /**
     * Сериализует сообщение в JSON.
     * Используется в StatusBroadcaster — упрощает его код.
     *
     * @throws JsonProcessingException если сериализация не удалась
     */
    public String toJson(Object message) throws JsonProcessingException {
        return objectMapper.writeValueAsString(message);
    }
}
