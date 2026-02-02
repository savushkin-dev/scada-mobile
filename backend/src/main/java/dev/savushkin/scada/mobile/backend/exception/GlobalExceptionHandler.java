package dev.savushkin.scada.mobile.backend.exception;

import dev.savushkin.scada.mobile.backend.dto.ErrorResponseDTO;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.net.SocketException;

/**
 * Глобальный обработчик исключений для всех REST контроллеров.
 * Централизовано обрабатывает исключения и возвращает структурированные ответы об ошибках.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Обрабатывает ошибки соединения с PrintSrv.
     *
     * @param e       исключение SocketException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с статусом 503 Service Unavailable
     */
    @ExceptionHandler(SocketException.class)
    public ResponseEntity<ErrorResponseDTO> handleSocketException(
            @NonNull SocketException e,
            @NonNull WebRequest request
    ) {
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponseDTO error = new ErrorResponseDTO(
                503, // Service Unavailable
                "PrintSrv недоступен: " + e.getMessage(),
                path
        );
        return ResponseEntity.status(503).body(error);
    }

    /**
     * Обрабатывает ошибки ввода-вывода (JSON парсинг, чтение из сокета и т.д.).
     *
     * @param e       исключение IOException
     * @param request объект запроса WebRequest
     * @return ResponseEntity с статусом 500 Internal Server Error
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponseDTO> handleIOException(
            @NonNull IOException e,
            @NonNull WebRequest request
    ) {
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponseDTO error = new ErrorResponseDTO(
                500, // Internal Server Error
                "Ошибка обработки данных: " + e.getMessage(),
                path
        );
        return ResponseEntity.internalServerError().body(error);
    }

    /**
     * Обрабатывает общие необработанные исключения (fallback).
     *
     * @param e       исключение Exception
     * @param request объект запроса WebRequest
     * @return ResponseEntity с статусом 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGlobalException(
            @SuppressWarnings("unused") Exception e,
            @NonNull WebRequest request
    ) {
        // Исключение может быть залогировано здесь в продакшене:
        // log.error("Unhandled exception", e);
        String path = request.getDescription(false).replace("uri=", "");
        ErrorResponseDTO error = new ErrorResponseDTO(
                500,
                "Внутренняя ошибка сервера",
                path
        );
        return ResponseEntity.internalServerError().body(error);
    }
}
