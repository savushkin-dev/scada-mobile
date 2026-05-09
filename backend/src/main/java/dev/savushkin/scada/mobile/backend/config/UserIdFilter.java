package dev.savushkin.scada.mobile.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet-фильтр, извлекающий идентификатор пользователя из заголовка {@code X-User-Id}.
 * <p>
 * Для каждого входящего HTTP-запроса (включая REST):
 * <ol>
 *   <li>Читает заголовок {@code X-User-Id}.</li>
 *   <li>Кладёт значение в request attribute {@code "userId"} и MDC {@code "userId"}.</li>
 *   <li>Гарантированно очищает MDC в {@code finally}.</li>
 * </ol>
 * <p>
 * Значение из request attribute используется контроллерами и сервисами для идентификации
 * пользователя. Значение MDC — для логирования.
 * <p>
 * <b>Временное решение:</b> заголовок передаётся клиентом без проверки подписи.
 * В дальнейшем будет заменён на JWT-аутентификацию.
 *
 * <h3>Потокобезопасность</h3>
 * Stateless ({@link OncePerRequestFilter}), request attribute scope — thread-local per request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // После MdcFilter
public class UserIdFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ID_REQUEST_ATTR = "userId";
    private static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                userId = userId.trim();
                request.setAttribute(USER_ID_REQUEST_ATTR, userId);
                MDC.put(MDC_USER_ID, userId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER_ID);
        }
    }

    /**
     * Извлекает userId из request attribute, установленного фильтром.
     *
     * @return userId или {@code null}, если заголовок не был передан
     */
    public static String resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(USER_ID_REQUEST_ATTR);
        return attr instanceof String s && !s.isBlank() ? s : null;
    }
}
