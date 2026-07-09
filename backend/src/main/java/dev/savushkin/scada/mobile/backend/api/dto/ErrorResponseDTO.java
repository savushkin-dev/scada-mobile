package dev.savushkin.scada.mobile.backend.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO для представления ошибки в REST API.
 * Содержит информацию об ошибке для клиента.
 */
public record ErrorResponseDTO(
        int status,
        String message,
        LocalDateTime timestamp,
        String path,
        Map<String, String> errors
) {
    /**
     * Конструктор с автоматической установкой текущего времени.
     *
     * @param status  HTTP статус-код
     * @param message описание ошибки
     * @param path    путь к endpoint, где произошла ошибка
     */
    public ErrorResponseDTO(int status, String message, String path) {
        this(status, message, LocalDateTime.now(), path, null);
    }

    /**
     * Конструктор с картой ошибок по полям (для валидационных/конфликтных ошибок).
     *
     * @param status  HTTP статус-код
     * @param message описание ошибки
     * @param path    путь к endpoint, где произошла ошибка
     * @param errors  ошибки по полям формы
     */
    public ErrorResponseDTO(int status, String message, String path, Map<String, String> errors) {
        this(status, message, LocalDateTime.now(), path, errors);
    }
}
