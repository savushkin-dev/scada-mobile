package dev.savushkin.scada.mobile.backend.config;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Handshake interceptor, извлекающий идентификатор пользователя при WebSocket-коннекте.
 * <p>
 * Стандартный JS {@code new WebSocket(url)} не позволяет задать HTTP-заголовки,
 * поэтому userId передаётся query-параметром: {@code /ws/live?userId=ivanov}.
 * <p>
 * Interceptor извлекает {@code userId} из query и кладёт в WebSocket session attributes
 * под ключом {@value #ATTR_USER_ID}. Если параметр отсутствует — атрибут не устанавливается
 * (сессия работает как анонимная).
 * <p>
 * <b>Временное решение:</b> userId не валидируется на сервере.
 * В дальнейшем будет заменён на JWT через STOMP/CONNECT headers.
 */
@Component
public class WebSocketUserIdInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketUserIdInterceptor.class);

    /**
     * Ключ атрибута WebSocket session для userId.
     * Используется хендлерами ({@code LiveWsHandler}, {@code UnitWsHandler}) для определения
     * отправителя сообщений.
     */
    public static final String ATTR_USER_ID = "userId";

    private static final String QUERY_PARAM = "userId";

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        String userId = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(QUERY_PARAM);

        if (userId != null && !userId.isBlank()) {
            userId = userId.trim();
            Long numericUserId = parseUserId(userId);
            if (numericUserId != null) {
                attributes.put(ATTR_USER_ID, numericUserId);
                log.debug("WS handshake: userId='{}' from URI='{}'", numericUserId, request.getURI());
            } else {
                log.debug("WS handshake: invalid userId='{}' from URI='{}'", userId, request.getURI());
            }
        } else {
            log.debug("WS handshake: no userId in query, URI='{}'", request.getURI());
        }

        // Всегда разрешаем handshake — userId опционален
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception
    ) {
        // Ничего не делаем после handshake
    }

    private Long parseUserId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
