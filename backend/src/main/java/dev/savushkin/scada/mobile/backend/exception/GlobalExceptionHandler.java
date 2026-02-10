package dev.savushkin.scada.mobile.backend.exception;

import dev.savushkin.scada.mobile.backend.dto.ErrorResponseDTO;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;
import java.net.SocketException;

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
        // Логируем полную информацию об ошибке
        log.error("SocketException occurred", e);

        // Извлекаем путь запроса для ответа
        String path = request.getDescription(false).replace("uri=", "");

        // Формируем структурированный ответ об ошибке
        ErrorResponseDTO error = new ErrorResponseDTO(
                503, // Service Unavailable - внешний сервис недоступен
                "PrintSrv недоступен: " + e.getMessage(),
                path
        );
        return ResponseEntity.status(503).body(error);
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
        // Логируем полную информацию об ошибке
        log.error("IOException occurred", e);

        // Извлекаем путь запроса для ответа
        String path = request.getDescription(false).replace("uri=", "");

        // Формируем структурированный ответ об ошибке
        ErrorResponseDTO error = new ErrorResponseDTO(
                500, // Internal Server Error
                "Ошибка обработки данных: " + e.getMessage(),
                path
        );
        return ResponseEntity.internalServerError().body(error);
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
        // Логируем информацию об ошибке
        log.warn("IllegalStateException occurred: {}", e.getMessage());

        // Извлекаем путь запроса для ответа
        String path = request.getDescription(false).replace("uri=", "");

        // Формируем структурированный ответ об ошибке
        ErrorResponseDTO error = new ErrorResponseDTO(
                503, // Service Unavailable - сервис не готов
                e.getMessage(),
                path
        );
        return ResponseEntity.status(503).body(error);
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
        // Логируем информацию об ошибке
        log.warn("IllegalArgumentException occurred: {}", e.getMessage());

        // Извлекаем путь запроса для ответа
        String path = request.getDescription(false).replace("uri=", "");

        // Формируем структурированный ответ об ошибке
        ErrorResponseDTO error = new ErrorResponseDTO(
                400, // Bad Request - некорректные данные
                "Некорректный запрос: " + e.getMessage(),
                path
        );
        return ResponseEntity.badRequest().body(error);
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
        // Логируем информацию об ошибке
        log.warn("Missing required parameter: {} (type: {})", e.getParameterName(), e.getParameterType());

        // Извлекаем путь запроса для ответа
        String path = request.getDescription(false).replace("uri=", "");

        // Формируем структурированный ответ об ошибке
        ErrorResponseDTO error = new ErrorResponseDTO(
                400, // Bad Request
                String.format("Отсутствует обязательный параметр: %s (тип: %s)",
                        e.getParameterName(), e.getParameterType()),
                path
        );
        return ResponseEntity.badRequest().body(error);
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
        // Логируем информацию об ошибке
        log.warn("Type mismatch for parameter: {} (value: {}, required type: {})",
                e.getName(), e.getValue(), e.getRequiredType());

        // Извлекаем путь запроса для ответа
        String path = request.getDescription(false).replace("uri=", "");

        // Формируем структурированный ответ об ошибке
        String requiredType = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown";
        ErrorResponseDTO error = new ErrorResponseDTO(
                400, // Bad Request
                String.format("Неверный тип параметра '%s': ожидается %s, получено '%s'",
                        e.getName(), requiredType, e.getValue()),
                path
        );
        return ResponseEntity.badRequest().body(error);
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
        // это ожидаемая ситуация в режиме деградации, поэтому warn достаточно
        log.warn("BufferOverflowException occurred: {}", e.getMessage());

        String path = request.getDescription(false).replace("uri=", "");

        ErrorResponseDTO error = new ErrorResponseDTO(
                503,
                e.getMessage(),
                path
        );

        return ResponseEntity.status(503).body(error);
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
        // Логируем полную информацию об ошибке с stack trace
        log.error("Unhandled exception occurred", e);

        // Извлекаем путь запроса для ответа
        String path = request.getDescription(false).replace("uri=", "");

        // Формируем общий ответ об ошибке (без деталей)
        ErrorResponseDTO error = new ErrorResponseDTO(
                500,
                "Внутренняя ошибка сервера", // Не раскрываем детали клиенту
                path
        );
        return ResponseEntity.internalServerError().body(error);
    }
}
