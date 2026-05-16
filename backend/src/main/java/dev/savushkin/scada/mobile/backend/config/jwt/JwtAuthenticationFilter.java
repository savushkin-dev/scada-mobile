package dev.savushkin.scada.mobile.backend.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT-фильтр аутентификации. Заменяет {@link UserIdFilter}.
 * <p>
 * Для каждого входящего HTTP-запроса:
 * <ol>
 *   <li>Пропускает публичные пути (логин, logout, refresh, actuator, topology).</li>
 *   <li>Извлекает {@code Authorization: Bearer <token>} заголовок.</li>
 *   <li>Валидирует access-токен через {@link JwtTokenProvider}.</li>
 *   <li>Кладёт userId в request attribute {@code "userId"} и MDC {@code "userId"}.</li>
 *   <li>При невалидном/отсутствующем токене возвращает 401 Unauthorized.</li>
 * </ol>
 * <p>
 * Контроллеры получают userId через {@link #resolveUserId(HttpServletRequest)} —
 * интерфейс совместим с предыдущим {@link UserIdFilter#resolveUserId}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_REQUEST_ATTR = "userId";
    private static final String MDC_USER_ID = "userId";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, JwtProperties jwtProperties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // CORS preflight — пропускаем без проверки токена
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Публичные пути — пропускаем без проверки токена
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = resolveToken(request);
            if (token == null) {
                sendUnauthorized(response, "Missing Authorization header");
                return;
            }

            Long userId = jwtTokenProvider.validateAccessToken(token);
            if (userId == null) {
                sendUnauthorized(response, "Invalid or expired token");
                return;
            }

            request.setAttribute(USER_ID_REQUEST_ATTR, Long.toString(userId));
            MDC.put(MDC_USER_ID, Long.toString(userId));

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
        }
    }

    /**
     * Извлекает userId из request attribute, установленного фильтром.
     * Обратно совместим с {@link UserIdFilter#resolveUserId}.
     *
     * @return userId или {@code null}, если токен не был валидирован
     */
    public static String resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(USER_ID_REQUEST_ATTR);
        return attr instanceof String s && !s.isBlank() ? s : null;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private boolean isPublicPath(String requestUri) {
        List<String> publicPaths = jwtProperties.getPublicPaths();
        if (publicPaths == null) return false;
        for (String pattern : publicPaths) {
            if (pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":\"error\",\"message\":\"" + message + "\"}");
    }
}
