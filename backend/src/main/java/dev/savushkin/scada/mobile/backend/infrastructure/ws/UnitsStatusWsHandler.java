package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket-хендлер для канала {@code /ws/workshops/{workshopId}/units/status}.
 * <p>
 * Клиент устанавливает соединение по URL, содержащему конкретный {@code workshopId}.
 * Хендлер извлекает {@code workshopId} из URI сессии и группирует сессии в карту
 * {@code workshopId → Set<WebSocketSession>}.
 * <p>
 * Рассылка инициируется {@link StatusBroadcaster}:
 * <ul>
 *   <li>{@link #broadcastToWorkshop(String, String)} — всем клиентам конкретного цеха</li>
 * </ul>
 * Клиент ничего не отправляет — канал только для чтения с его стороны.
 */
@Component
public class UnitsStatusWsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UnitsStatusWsHandler.class);

    /**
     * workshopId → активные сессии
     */
    private final Map<String, Set<WebSocketSession>> sessionsByWorkshop = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String workshopId = extractWorkshopId(session);
        if (workshopId == null) {
            log.warn("WS units/status: cannot extract workshopId from URI {}, closing", session.getUri());
            closeQuietly(session);
            return;
        }
        sessionsByWorkshop
                .computeIfAbsent(workshopId, k -> new CopyOnWriteArraySet<>())
                .add(session);
        log.debug("WS units/status: client connected, workshop={}, sessionId={}, total={}",
                workshopId, session.getId(), sessionsByWorkshop.get(workshopId).size());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String workshopId = extractWorkshopId(session);
        if (workshopId != null) {
            Set<WebSocketSession> set = sessionsByWorkshop.get(workshopId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) sessionsByWorkshop.remove(workshopId);
            }
        }
        log.debug("WS units/status: client disconnected, sessionId={}, reason={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.warn("WS units/status: transport error, sessionId={}: {}", session.getId(), exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * Рассылает JSON-сообщение всем клиентам, подключённым к каналу данного цеха.
     *
     * @param workshopId идентификатор цеха
     * @param json       сериализованное {@link dev.savushkin.scada.mobile.backend.api.dto.UnitsStatusMessageDTO}
     */
    public void broadcastToWorkshop(String workshopId, String json) {
        Set<WebSocketSession> sessions = sessionsByWorkshop.get(workshopId);
        if (sessions == null || sessions.isEmpty()) return;
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("WS units/status: send failed, workshop={}, sessionId={}: {}",
                        workshopId, session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    /**
     * Извлекает {@code workshopId} из URI сессии.
     * Ожидаемый формат: {@code /ws/workshops/{workshopId}/units/status}
     */
    private @Nullable String extractWorkshopId(@NonNull WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        // Путь вида: /ws/workshops/dess/units/status
        String path = uri.getPath();
        if (path == null) return null;
        // Ищем сегмент между /workshops/ и /units/status
        String prefix = "/ws/workshops/";
        String suffix = "/units/status";
        int start = path.indexOf(prefix);
        int end = path.indexOf(suffix);
        if (start < 0 || end < 0 || end <= start + prefix.length()) return null;
        String id = path.substring(start + prefix.length(), end);
        return id.isBlank() ? null : id;
    }

    private void closeQuietly(@NonNull WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) { /* уже закрыта */ }
    }

    /**
     * Число активных цехов с подключёнными клиентами — для диагностики.
     */
    public int getActiveWorkshopCount() {
        return sessionsByWorkshop.size();
    }

    /**
     * Возвращает набор workshopId, у которых есть хотя бы один подключённый клиент.
     * Используется {@link StatusBroadcaster} для адресной рассылки.
     */
    public Set<String> getActiveWorkshopIds() {
        return sessionsByWorkshop.keySet();
    }
}
