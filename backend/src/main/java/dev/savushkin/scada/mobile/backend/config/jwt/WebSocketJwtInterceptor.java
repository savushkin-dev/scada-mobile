package dev.savushkin.scada.mobile.backend.config.jwt;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import io.jsonwebtoken.Claims;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
 * Поддерживает два типа субъектов (claim {@code subject_type}):
 * <ul>
 *   <li>{@code "user"} (или claim отсутствует) — работник; в session attributes
 *       кладутся {@value #ATTR_USER_ID} (Long) и {@value #ATTR_ROLE};</li>
 *   <li>{@code "machine"} — автомат / СКАДА; кладутся {@value #ATTR_SUBJECT_TYPE}
 *       и {@value #ATTR_MACHINE_UNIT} (PrintSrv instance id из sub). Токен дополнительно
 *       проверяется по актуальной записи автомата в {@code units}.</li>
 * </ul>
 * <p>
 * Если токен отсутствует или невалиден — handshake ОТКЛОНЯЕТСЯ с 401.
 * Анонимные WebSocket-соединения не допускаются.
 */
@Component
public class WebSocketJwtInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketJwtInterceptor.class);

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "role";
    /** Тип субъекта сессии: {@code "user"} (по умолчанию) или {@code "machine"}. */
    public static final String ATTR_SUBJECT_TYPE = "subjectType";
    /** PrintSrv instance id автомата — только для machine-сессий. */
    public static final String ATTR_MACHINE_UNIT = "machineUnit";
    private static final String QUERY_PARAM = "token";

    private final JwtTokenProvider jwtTokenProvider;
    private final UnitJpaRepository unitRepository;

    public WebSocketJwtInterceptor(JwtTokenProvider jwtTokenProvider,
                                   UnitJpaRepository unitRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.unitRepository = unitRepository;
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

        var claims = jwtTokenProvider.validateAccessTokenClaims(token.trim());
        if (claims == null) {
            log.warn("WS handshake rejected: invalid token, URI='{}'", request.getURI());
            response.setStatusCode(HttpStatusCode.valueOf(401));
            return false;
        }

        if (JwtTokenProvider.SUBJECT_TYPE_MACHINE.equals(
                claims.get(JwtTokenProvider.SUBJECT_TYPE_CLAIM, String.class))) {
            return acceptMachine(request, response, attributes, claims);
        }

        Long userId = parseUserId(claims.getSubject());
        if (userId == null) {
            log.warn("WS handshake rejected: invalid subject, URI='{}'", request.getURI());
            response.setStatusCode(HttpStatusCode.valueOf(401));
            return false;
        }

        attributes.put(ATTR_USER_ID, userId);
        attributes.put(ATTR_ROLE, claims.get("role", String.class));
        log.debug("WS handshake: authenticated userId='{}' URI='{}'", userId, request.getURI());
        return true;
    }

    /**
     * Аутентификация автомата (СКАДА): проверяет, что machine-токен
    * существует и активен в {@code units}, а claim {@code unitId} согласован с ним.
     */
    private boolean acceptMachine(ServerHttpRequest request,
                                  ServerHttpResponse response,
                                  Map<String, Object> attributes,
                                  Claims claims) {
        String jti = claims.getId();
        String machineUnit = claims.getSubject();
        Long unitId = claims.get(JwtTokenProvider.MACHINE_UNIT_ID_CLAIM, Long.class);
        if (jti == null || machineUnit == null || machineUnit.isBlank() || unitId == null
            || unitRepository.findByPrintsrvInstanceId(machineUnit)
            .filter(unit -> unit.isActive() && unit.getId().equals(unitId))
            .isEmpty()) {
            log.warn("WS handshake rejected: machine token revoked/unknown, jti='{}', URI='{}'",
                    jti, request.getURI());
            response.setStatusCode(HttpStatusCode.valueOf(401));
            return false;
        }

        attributes.put(ATTR_SUBJECT_TYPE, JwtTokenProvider.SUBJECT_TYPE_MACHINE);
        attributes.put(ATTR_MACHINE_UNIT, machineUnit);
        log.debug("WS handshake: authenticated machine='{}' URI='{}'", machineUnit, request.getURI());
        return true;
    }

    private @Nullable Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(subject.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
