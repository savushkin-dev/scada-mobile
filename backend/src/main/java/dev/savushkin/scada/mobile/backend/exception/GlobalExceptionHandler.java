package dev.savushkin.scada.mobile.backend.exception;

import dev.savushkin.scada.mobile.backend.api.dto.ErrorResponseDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.SocketException;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений для всех REST контроллеров.
 * <p>
 * Централизованно обрабатывает исключения и возвращает структурированные
 * ответы об ошибках в формате {@link ErrorResponseDTO}.
 * <p>
 * Типы обрабатываемых исключений:
 * <ul>
 *   <li>{@link SocketException} - проблемы с соединением PrintSrv (503)</li>
 *   <li>{@link IOException} - ошибки I/O, JSON парсинга (500)</li>
 *   <li>{@link IllegalStateException} - некорректное состояние приложения (503)</li>
 *   <li>{@link IllegalArgumentException} - некорректные параметры запроса (400)</li>
 *   <li>{@link MissingServletRequestParameterException} - отсутствует обязательный параметр (400)</li>
 *   <li>{@link MethodArgumentTypeMismatchException} - неверный тип параметра (400)</li>
 *   <li>{@link Exception} - все остальные необработанные исключения (500)</li>
 * </ul>
 * <p>
 * Все исключения логируются с полным stack trace для отладки.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        private static final String INTERNAL_ERROR_MESSAGE = "Внутренняя ошибка сервера";

        private @NonNull String extractPath(@NonNull WebRequest request) {
                return request.getDescription(false).replace("uri=", "");
        }

        private @NonNull ResponseEntity<ErrorResponseDTO> buildErrorResponse(
                        @NonNull HttpStatusCode status,
                        @NonNull String message,
                        @NonNull WebRequest request
        ) {
                String path = extractPath(request);
                ErrorResponseDTO error = new ErrorResponseDTO(status.value(), message, path);
                return ResponseEntity.status(status).body(error);
        }

    /**
     * Обрабатывает ошибки socket-соединения с PrintSrv.
     * <p>
     * Возвращает HTTP 503 (Service Unavailable), так как проблема
     * связана с недоступностью внешнего сервиса (PrintSrv).
     * <p>
     * Типичные причины:
     * <ul>
     *   <li>PrintSrv не запущен</li>
     *   <li>Неверный IP/порт в конфигурации</li>
     *   <li>Сетевые проблемы</li>
     *   <li>Socket был закрыт</li>
     * </ul>
     *
     * @param e       исключение SocketException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с ErrorResponseDTO и статусом 503
     */
    @ExceptionHandler(SocketException.class)
    public ResponseEntity<ErrorResponseDTO> handleSocketException(
            @NonNull SocketException e,
            @NonNull WebRequest request
    ) {
        log.error("SocketException occurred", e);
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "PrintSrv недоступен: " + e.getMessage(), request);
    }

    /**
     * Обрабатывает ошибки ввода-вывода.
     * <p>
     * Возвращает HTTP 500 (Internal Server Error), так как проблема
     * связана с обработкой данных внутри приложения.
     * <p>
     * Типичные причины:
     * <ul>
     *   <li>Ошибка парсинга JSON от PrintSrv (JsonProcessingException)</li>
     *   <li>Ошибка чтения/записи из socket</li>
     *   <li>Некорректный формат данных от PrintSrv</li>
     * </ul>
     *
     * @param e       исключение IOException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с ErrorResponseDTO и статусом 500
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponseDTO> handleIOException(
            @NonNull IOException e,
            @NonNull WebRequest request
    ) {
        log.error("IOException occurred", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка обработки данных: " + e.getMessage(), request);
    }

    /**
     * Обрабатывает ошибки некорректного состояния приложения.
     * <p>
     * Возвращает HTTP 503 (Service Unavailable), так как проблема
     * связана с неготовностью сервиса к обработке запросов.
     * <p>
     * Типичные причины:
     * <ul>
     *   <li>Snapshot PrintSrv еще не загружен (приложение только запустилось)</li>
     *   <li>Необходимые данные недоступны</li>
     *   <li>Сервис в процессе инициализации</li>
     * </ul>
     *
     * @param e       исключение IllegalStateException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с ErrorResponseDTO и статусом 503
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalStateException(
            @NonNull IllegalStateException e,
            @NonNull WebRequest request
    ) {
        log.warn("IllegalStateException occurred: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    /**
     * Обрабатывает ошибки некорректных параметров запроса.
     * <p>
     * Возвращает HTTP 400 (Bad Request), так как проблема
     * связана с некорректными данными от клиента.
     * <p>
     * Типичные причины:
     * <ul>
     *   <li>Пустой JSON запрос к PrintSrv</li>
     *   <li>Пустой ответ от PrintSrv</li>
     *   <li>Некорректные параметры метода</li>
     * </ul>
     *
     * @param e       исключение IllegalArgumentException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с ErrorResponseDTO и статусом 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(
            @NonNull IllegalArgumentException e,
            @NonNull WebRequest request
    ) {
        log.warn("IllegalArgumentException occurred: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Некорректный запрос: " + e.getMessage(), request);
    }

    /**
     * Обрабатывает ошибки отсутствия обязательного параметра запроса.
     * <p>
     * Возвращает HTTP 400 (Bad Request), так как клиент не предоставил
     * обязательный параметр (например, отсутствует ?unit= или ?value=).
     * <p>
     * Типичные причины:
     * <ul>
     *   <li>Клиент забыл передать параметр unit в /setUnitVars</li>
     *   <li>Клиент забыл передать параметр value в /setUnitVars</li>
     * </ul>
     *
     * @param e       исключение MissingServletRequestParameterException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с ErrorResponseDTO и статусом 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParameter(
            @NonNull MissingServletRequestParameterException e,
            @NonNull WebRequest request
    ) {
        log.warn("Missing required parameter: {} (type: {})", e.getParameterName(), e.getParameterType());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                String.format("Отсутствует обязательный параметр: %s (тип: %s)", e.getParameterName(), e.getParameterType()),
                request
        );
    }

    /**
     * Обрабатывает ошибки несоответствия типа параметра запроса.
     * <p>
     * Возвращает HTTP 400 (Bad Request), так как клиент передал
     * параметр неверного типа (например, строку вместо числа).
     * <p>
     * Типичные причины:
     * <ul>
     *   <li>Передали ?unit=abc вместо ?unit=1</li>
     *   <li>Передали ?value=xyz вместо ?value=555</li>
     * </ul>
     *
     * @param e       исключение MethodArgumentTypeMismatchException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с ErrorResponseDTO и статусом 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(
            @NonNull MethodArgumentTypeMismatchException e,
            @NonNull WebRequest request
    ) {
        log.warn("Type mismatch for parameter: {} (value: {}, required type: {})",
                e.getName(), e.getValue(), e.getRequiredType());
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                String.format("Неверный тип параметра '%s': ожидается %s, получено '%s'",
                        e.getName(), requiredType, e.getValue()),
                request
        );
    }

    /**
     * Обрабатывает переполнение буфера команд (degraded mode).
     * <p>
     * Возвращает HTTP 503 (Service Unavailable), т.к. система не может принять
     * новые команды записи из-за длительной недоступности PrintSrv и переполненного
     * буфера.
     * <p>
     * В ответе не раскрываем внутренние детали (размеры буфера и т.п.), но даём
     * понятный для клиента hint.
     */
    @ExceptionHandler(BufferOverflowException.class)
    public ResponseEntity<ErrorResponseDTO> handleBufferOverflowException(
            @NonNull BufferOverflowException e,
            @NonNull WebRequest request
    ) {
        log.warn("BufferOverflowException occurred: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    /**
     * Обрабатывает ошибку отсутствующего маршрута/ресурса.
     * Возвращает корректный HTTP 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoResourceFoundException(
            @NonNull NoResourceFoundException e,
            @NonNull WebRequest request
    ) {
        log.warn("No resource found: method={}, resourcePath={}", e.getHttpMethod(), e.getResourcePath());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Ресурс не найден", request);
    }

    /**
     * Обрабатывает запросы с неподдерживаемым HTTP-методом.
     * Возвращает HTTP 405.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(
            @NonNull HttpRequestMethodNotSupportedException e,
            @NonNull WebRequest request
    ) {
        String supported = e.getSupportedHttpMethods() == null || e.getSupportedHttpMethods().isEmpty()
                ? "не указаны"
                : e.getSupportedHttpMethods().stream().map(Object::toString).collect(Collectors.joining(", "));

        log.warn("Method not supported: method={}, supported={}", e.getMethod(), supported);
        return buildErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED,
                String.format("Метод '%s' не поддерживается. Разрешены: %s", e.getMethod(), supported),
                request
        );
    }

    /**
     * Обрабатывает неподдерживаемый Content-Type.
     * Возвращает HTTP 415.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotSupported(
            @NonNull HttpMediaTypeNotSupportedException e,
            @NonNull WebRequest request
    ) {
        String contentType = e.getContentType() != null ? e.getContentType().toString() : "unknown";
        log.warn("Unsupported media type: {}", contentType);
        return buildErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Неподдерживаемый Content-Type: " + contentType,
                request
        );
    }

    /**
     * Обрабатывает невозможность отдать ответ в Accept-формате клиента.
     * Возвращает HTTP 406.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotAcceptable(
            @NonNull HttpMediaTypeNotAcceptableException e,
            @NonNull WebRequest request
    ) {
        log.warn("Not acceptable media type requested");
        return buildErrorResponse(HttpStatus.NOT_ACCEPTABLE, "Невозможно вернуть ответ в запрошенном формате", request);
    }

    /**
     * Обрабатывает ошибки парсинга тела запроса (например, некорректный JSON).
     * Возвращает HTTP 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMessageNotReadable(
            @NonNull HttpMessageNotReadableException e,
            @NonNull WebRequest request
    ) {
        log.warn("Request body is not readable: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Некорректное тело запроса", request);
    }

    /**
     * Обрабатывает ошибки валидации аргументов/модели биндинга.
     * Возвращает HTTP 400.
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponseDTO> handleValidationExceptions(
            @NonNull Exception e,
            @NonNull WebRequest request
    ) {
        log.warn("Validation error: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Ошибка валидации входных данных", request);
    }

    /**
     * Обрабатывает исключения, где статус уже определён бизнес-логикой/фреймворком.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDTO> handleResponseStatusException(
            @NonNull ResponseStatusException e,
            @NonNull WebRequest request
    ) {
        HttpStatusCode status = e.getStatusCode();
        String message = e.getReason() != null && !e.getReason().isBlank()
                ? e.getReason()
                : "Ошибка обработки запроса";

        log.warn("ResponseStatusException occurred: status={}, message={}", status.value(), message);
        return buildErrorResponse(status, message, request);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolations(
            @NonNull Exception e,
            @NonNull WebRequest request
    ) {
        log.warn("Constraint violation: {}", e.getMessage());

        String details;
        if (e instanceof ConstraintViolationException cve) {
            details = cve.getConstraintViolations().stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(this::formatViolation)
                    .collect(Collectors.joining("; "));
        } else {
            // HandlerMethodValidationException (Spring MVC method validation)
            // Не раскрываем лишние детали, но даём полезный hint.
            details = "Нарушены ограничения валидации параметров запроса";
        }

        String message = details.isBlank()
                ? "Ошибка валидации параметров запроса"
                : "Ошибка валидации параметров запроса: " + details;

        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    private String formatViolation(ConstraintViolation<?> v) {
        String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "unknown";
        String msg = v.getMessage() != null ? v.getMessage() : "invalid";
        return path + " " + msg;
    }

    /**
     * Обрабатывает все необработанные исключения (fallback handler).
     * <p>
     * Возвращает HTTP 500 (Internal Server Error) с общим сообщением
     * об ошибке (без деталей для безопасности).
     * <p>
     * Этот обработчик срабатывает для всех исключений, которые не были
     * обработаны более специфичными @ExceptionHandler методами.
     * <p>
     * <b>Важно:</b> Полная информация об ошибке логируется, но не возвращается
     * клиенту (для предотвращения утечки чувствительных данных).
     *
     * @param e       любое необработанное исключение
     * @param request объект запроса WebRequest
     * @return ResponseEntity с ErrorResponseDTO и статусом 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGlobalException(
            Exception e,
            @NonNull WebRequest request
    ) {
        log.error("Unhandled exception occurred", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request);
    }
}
