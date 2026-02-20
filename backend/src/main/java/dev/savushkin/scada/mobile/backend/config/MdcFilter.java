package dev.savushkin.scada.mobile.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet-фильтр, который привязывает диагностический контекст (MDC) к каждому HTTP-запросу.
 * <p>
 * Для каждого входящего запроса:
 * <ol>
 *   <li>Генерирует уникальный {@code requestId} (короткий UUID без дефисов, первые 8 символов)
 *       или принимает его из заголовка {@code X-Request-ID}, если он уже есть
 *       (полезно при трассировке через API Gateway / reverse proxy).</li>
 *   <li>Кладёт в MDC: {@code requestId}, {@code method}, {@code uri}.</li>
 *   <li>Гарантированно очищает MDC через {@code finally} — утечки контекста между запросами невозможны.</li>
 * </ol>
 * <p>
 * Благодаря этому каждая строка лога внутри обработчика запроса содержит
 * эти поля автоматически — без явных вызовов в бизнес-логике.
 * <p>
 * Пример строки лога (dev, plain text):
 * <pre>
 * 2026-02-20 15:00:01.123 INFO  [http-1] [a1b2c3d4] POST /api/v1/commands/setUnitVars CommandsController - SetUnitVars command accepted
 * </pre>
 * <p>
 * Пример в JSON (prod):
 * <pre>
 * { "requestId": "a1b2c3d4", "method": "POST", "uri": "/api/v1/commands/setUnitVars", ... }
 * </pre>
 * <p>
 * <b>Архитектурные гарантии:</b>
 * <ul>
 *   <li>Фильтр работает только один раз на запрос ({@link OncePerRequestFilter}).</li>
 *   <li>MDC очищается в {@code finally} — даже при исключениях и async dispatch.</li>
 *   <li>Не хранит состояние — stateless, thread-safe.</li>
 *   <li>Не зависит от domain/application слоёв — только инфраструктура.</li>
 * </ul>
 *
 * @see org.slf4j.MDC
 */
public class MdcFilter extends OncePerRequestFilter {

    /**
     * Имя HTTP-заголовка, из которого принимается внешний requestId.
     * Полезно при работе через nginx / API Gateway, который уже проставляет трассировочный ID.
     */
    static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * MDC-ключ для идентификатора запроса.
     */
    static final String MDC_REQUEST_ID = "requestId";

    /**
     * MDC-ключ для HTTP-метода (GET, POST, ...).
     */
    static final String MDC_METHOD = "method";

    /**
     * MDC-ключ для URI запроса.
     */
    static final String MDC_URI = "uri";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Используем внешний X-Request-ID если он валиден, иначе генерируем свой
            String requestId = resolveRequestId(request);

            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_METHOD, request.getMethod());
            MDC.put(MDC_URI, request.getRequestURI());

            // Пробрасываем requestId клиенту — удобно для корреляции в логах на стороне фронта
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);

        } finally {
            // ОБЯЗАТЕЛЬНО — иначе MDC «протекает» в следующий запрос в том же потоке
            // (HTTP thread pool переиспользует потоки)
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_METHOD);
            MDC.remove(MDC_URI);
        }
    }

    /**
     * Определяет requestId для запроса.
     * <p>
     * Если входящий заголовок {@code X-Request-ID} содержит непустое значение длиной до 64 символов
     * и состоит только из безопасных символов (буквы, цифры, дефис, подчёркивание) — используем его.
     * Иначе генерируем короткий UUID (8 символов hex, collision-safe для нашей нагрузки).
     *
     * @param request HTTP-запрос
     * @return строка-идентификатор запроса
     */
    private String resolveRequestId(HttpServletRequest request) {
        String external = request.getHeader(REQUEST_ID_HEADER);
        if (external != null && !external.isBlank() && external.length() <= 64
                && external.matches("[a-zA-Z0-9\\-_]+")) {
            return external;
        }
        // Первые 8 символов UUID без дефисов: "a1b2c3d4"
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}


