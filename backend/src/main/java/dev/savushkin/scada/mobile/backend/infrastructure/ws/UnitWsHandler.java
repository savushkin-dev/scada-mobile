package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.api.dto.DevicesStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.ErrorsMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.LineStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueueMessageDTO;
import dev.savushkin.scada.mobile.backend.services.UnitDetailService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket-хендлер канала {@code /ws/unit/{unitId}}.
 *
 * <p>Жизненный цикл одного соединения:
 * <ol>
 *   <li>Клиент открывает соединение на {@code /ws/unit/trepko2}.</li>
 *   <li>Хендлер извлекает {@code unitId} из URI-пути.</li>
 *   <li>Если {@code unitId} неизвестен конфигу — соединение закрывается с
 *       {@link CloseStatus#BAD_DATA statusCode=1007}.</li>
 *   <li>Посылаются все четыре начальных снапшота:
 *       {@code LINE_STATUS}, {@code DEVICES_STATUS}, {@code QUEUE}, {@code ERRORS}.</li>
 *   <li>При каждом polling-событии {@link StatusBroadcaster} вызывает
 *       {@link #broadcastToUnit(String)} — все подписчики данного аппарата
 *       получают обновлённые данные.</li>
 *   <li>При разрыве соединения или ошибке сессия удаляется из реестра.</li>
 * </ol>
 *
 * <h3>Потокобезопасность</h3>
 * {@code sessionsByUnit} — {@link ConcurrentHashMap} значений {@link CopyOnWriteArraySet}:
 * публикация {@link #broadcastToUnit(String)} безопасна из polling-потока параллельно
 * с добавлением/удалением сессий при connect/disconnect.
 *
 * <h3>Входящие сообщения</h3>
 * Данный канал — только push; клиент не отправляет никаких сообщений. Любое
 * входящее сообщение логируется как предупреждение и игнорируется.
 */
@Component
public class UnitWsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UnitWsHandler.class);

    /**
     * Атрибут сессии — идентификатор аппарата, извлечённый из URI.
     */
    private static final String ATTR_UNIT_ID = "unitId";

    /**
     * Суффикс пути: {@code /ws/unit/} — для быстрого извлечения unitId.
     */
    private static final String PATH_PREFIX = "/ws/unit/";

    private final UnitDetailService unitDetailService;
    private final ObjectMapper objectMapper;

    /**
     * unitId → активные сессии
     */
    private final Map<String, Set<WebSocketSession>> sessionsByUnit = new ConcurrentHashMap<>();

    public UnitWsHandler(UnitDetailService unitDetailService, ObjectMapper objectMapper) {
        this.unitDetailService = unitDetailService;
        this.objectMapper = objectMapper;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String unitId = extractUnitId(session);
        if (unitId == null) {
            log.warn("WS /unit: cannot extract unitId from URI='{}', closing", session.getUri());
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        if (!unitDetailService.isKnownInstance(unitId)) {
            log.warn("WS /unit: unknown unitId='{}', closing", unitId);
            session.close(new CloseStatus(CloseStatus.BAD_DATA.getCode(),
                    "Unknown unitId: " + unitId));
            return;
        }

        session.getAttributes().put(ATTR_UNIT_ID, unitId);
        sessionsByUnit.computeIfAbsent(unitId, k -> new CopyOnWriteArraySet<>()).add(session);

        sendInitialSnapshot(session, unitId);

        log.debug("WS /unit: connected unitId='{}', id={}, total={}",
                unitId, session.getId(),
                sessionsByUnit.getOrDefault(unitId, Set.of()).size());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        removeSession(session);
        log.debug("WS /unit: disconnected id={}, reason={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.warn("WS /unit: transport error id={}: {}", session.getId(), exception.getMessage());
        removeSession(session);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        // Этот канал — только server-push; входящие сообщения от клиента не ожидаются.
        log.warn("WS /unit: unexpected incoming message from id='{}', ignored", session.getId());
    }

    // ─── Push (server → client) ───────────────────────────────────────────────

    /**
     * Рассылает обновлённые данные по всем четырём типам сообщений
     * всем клиентам, подключённым к данному аппарату.
     *
     * <p>Вызывается {@link StatusBroadcaster} после каждого polling-события.
     * Если нет активных подписчиков — операция является no-op.
     *
     * @param instanceId идентификатор аппарата
     */
    public void broadcastToUnit(String instanceId) {
        Set<WebSocketSession> sessions = sessionsByUnit.get(instanceId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        trySend(instanceId, this::buildLineStatusJson, "LINE_STATUS", sessions);
        trySend(instanceId, this::buildDevicesStatusJson, "DEVICES_STATUS", sessions);
        trySend(instanceId, this::buildQueueJson, "QUEUE", sessions);
        trySend(instanceId, this::buildErrorsJson, "ERRORS", sessions);
    }

    /**
     * Число клиентов, подписанных на данный аппарат.
     * Используется {@link StatusBroadcaster} для проверки перед сборкой сообщений.
     */
    public int getSubscriberCount(String instanceId) {
        Set<WebSocketSession> sessions = sessionsByUnit.get(instanceId);
        return sessions == null ? 0 : sessions.size();
    }

    /**
     * Число аппаратов, у которых есть хотя бы один подписчик.
     */
    public int getActiveUnitCount() {
        return sessionsByUnit.size();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Отправляет начальный снапшот — все четыре сообщения сразу при подключении.
     * Ошибки сериализации/отправки не фатальны — клиент получит данные на следующем push.
     */
    private void sendInitialSnapshot(WebSocketSession session, String unitId) {
        for (String type : List.of("LINE_STATUS", "DEVICES_STATUS", "QUEUE", "ERRORS")) {
            try {
                String json = switch (type) {
                    case "LINE_STATUS"     -> buildLineStatusJson(unitId);
                    case "DEVICES_STATUS" -> buildDevicesStatusJson(unitId);
                    case "QUEUE"          -> buildQueueJson(unitId);
                    case "ERRORS"         -> buildErrorsJson(unitId);
                    default -> null;
                };
                sendSafely(session, json, type);
            } catch (JsonProcessingException e) {
                log.warn("WS /unit: failed to build initial {}, unitId='{}': {}", type, unitId, e.getMessage());
            }
        }
    }

    @FunctionalInterface
    private interface JsonBuilder {
        String build(String instanceId) throws JsonProcessingException;
    }

    private void trySend(String instanceId, JsonBuilder builder, String type,
                         Set<WebSocketSession> sessions) {
        try {
            String json = builder.build(instanceId);
            if (json == null) return;
            sendToSessions(sessions, json, type);
        } catch (JsonProcessingException e) {
            log.error("WS /unit: failed to serialize {} for unitId='{}': {}",
                    type, instanceId, e.getMessage());
        }
    }

    private void sendToSessions(Set<WebSocketSession> sessions, String json, String type) {
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                sendMessageSafely(session, json);
            } catch (IOException | IllegalStateException e) {
                log.warn("WS /unit: {} send failed, id={}: {}", type, session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    private void sendSafely(WebSocketSession session, String json, String type) {
        if (json == null || !session.isOpen()) return;
        try {
            sendMessageSafely(session, json);
        } catch (Exception e) {
            log.warn("WS /unit: failed to send initial {}, id={}: {}", type, session.getId(), e.getMessage());
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

    private String buildLineStatusJson(String instanceId) throws JsonProcessingException {
        LineStatusMessageDTO msg = unitDetailService.buildLineStatus(instanceId);
        return msg == null ? null : objectMapper.writeValueAsString(msg);
    }

    private String buildDevicesStatusJson(String instanceId) throws JsonProcessingException {
        DevicesStatusMessageDTO msg = unitDetailService.buildDevicesStatus(instanceId);
        return msg == null ? null : objectMapper.writeValueAsString(msg);
    }

    private String buildQueueJson(String instanceId) throws JsonProcessingException {
        QueueMessageDTO msg = unitDetailService.buildQueueStatus(instanceId);
        return msg == null ? null : objectMapper.writeValueAsString(msg);
    }

    private String buildErrorsJson(String instanceId) throws JsonProcessingException {
        ErrorsMessageDTO msg = unitDetailService.buildErrorsStatus(instanceId);
        return msg == null ? null : objectMapper.writeValueAsString(msg);
    }

    /**
     * Извлекает {@code unitId} из URI-пути сессии.
     * Путь должен иметь вид {@code /ws/unit/{unitId}}.
     * Возвращает {@code null} при некорректном URI.
     */
    private static String extractUnitId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        String path = uri.getPath();
        if (path == null || !path.startsWith(PATH_PREFIX)) return null;
        String unitId = path.substring(PATH_PREFIX.length());
        // Не допускаем пустой unitId или unitId с дополнительными сегментами пути
        if (unitId.isBlank() || unitId.contains("/")) return null;
        return unitId;
    }

    private void removeSession(WebSocketSession session) {
        String unitId = (String) session.getAttributes().get(ATTR_UNIT_ID);
        if (unitId == null) return;

        Set<WebSocketSession> sessions = sessionsByUnit.get(unitId);
        if (sessions == null) return;

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUnit.remove(unitId, sessions);
        }
    }
}
