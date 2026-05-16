package dev.savushkin.scada.mobile.backend.config.jwt;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket handshake interceptor с JWT-аутентификацией.
 * <p>
 * Извлекает access-токен из query-параметра {@code ?token=<jwt>}
 * (браузер не может задать кастомные HTTP-заголовки в {@code new WebSocket()}).
 * <p>
 * Валидирует токен через {@link JwtTokenProvider} и кладёт userId (Long)
 * в session attributes под ключом {@value #ATTR_USER_ID}.
 * <p>
 * Если токен отсутствует или невалиден — handshake ОТКЛОНЯЕТСЯ с 401.
 * Анонимные WebSocket-соединения не допускаются.
 */
@Component
public class WebSocketJwtInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketJwtInterceptor.class);

    public static final String ATTR_USER_ID = "userId";
    private static final String QUERY_PARAM = "token";

    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketJwtInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(QUERY_PARAM);

        if (token == null || token.isBlank()) {
            log.warn("WS handshake rejected: missing token, URI='{}'", request.getURI());
            response.setStatusCode(HttpStatusCode.valueOf(401));
            return false;
        }

        Long userId = jwtTokenProvider.validateAccessToken(token.trim());
        if (userId == null) {
            log.warn("WS handshake rejected: invalid token, URI='{}'", request.getURI());
            response.setStatusCode(HttpStatusCode.valueOf(401));
            return false;
        }

        attributes.put(ATTR_USER_ID, userId);
        log.debug("WS handshake: authenticated userId='{}' URI='{}'", userId, request.getURI());
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception
    ) {
        // nothing
    }
}
