package dev.savushkin.scada.mobile.backend.infrastructure.ws;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket-хендлер для канала {@code /ws/workshops/status}.
 * <p>
 * Поддерживает реестр всех активных сессий. Рассылка инициируется
 * {@link StatusBroadcaster} после каждого scan cycle — сервер всегда
 * пушит данные клиентам, клиент не отправляет запросов.
 * <p>
 * Потокобезопасность: {@link CopyOnWriteArraySet} позволяет безопасно
 * итерировать при рассылке и модифицировать при подключении/отключении.
 */
@Component
public class WorkshopsStatusWsHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkshopsStatusWsHandler.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
        log.debug("WS /workshops/status: client connected, id={}, total={}",
                session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
        log.debug("WS /workshops/status: client disconnected, id={}, reason={}, remaining={}",
                session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.warn("WS /workshops/status: transport error, id={}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /**
     * Рассылает JSON-сообщение всем подключённым клиентам.
     * Закрытые/прерванные сессии пропускаются и удаляются из реестра.
     *
     * @param json сериализованное JSON-сообщение ({@link dev.savushkin.scada.mobile.backend.api.dto.WorkshopsStatusMessageDTO})
     */
    public void broadcast(String json) {
        if (sessions.isEmpty()) return;
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                log.warn("WS /workshops/status: send failed, id={}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    /**
     * Количество активных подключений — для метрик/diagnostics.
     */
    public int getSessionCount() {
        return sessions.size();
    }
}
