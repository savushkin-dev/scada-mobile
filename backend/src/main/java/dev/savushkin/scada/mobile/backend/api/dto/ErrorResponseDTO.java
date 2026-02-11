package dev.savushkin.scada.mobile.backend.api.dto;

import java.time.LocalDateTime;

/**
 * DTO для представления ошибки в REST API.
 * Содержит информацию об ошибке для клиента.
 */
public record ErrorResponseDTO(
        int status,
        String message,
        LocalDateTime timestamp,
        String path
) {
    /**
     * Конструктор с автоматической установкой текущего времени.
     *
     * @param status  HTTP статус-код
     * @param message описание ошибки
     * @param path    путь к endpoint, где произошла ошибка
     */
    public ErrorResponseDTO(int status, String message, String path) {
        this(status, message, LocalDateTime.now(), path);
    }
}
