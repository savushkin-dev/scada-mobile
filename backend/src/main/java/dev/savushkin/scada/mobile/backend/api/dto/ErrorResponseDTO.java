package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
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
        Map<String, String> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<ReferenceDTO> references
) {
    /**
     * Конструктор с автоматической установкой текущего времени.
     *
     * @param status  HTTP статус-код
     * @param message описание ошибки
     * @param path    путь к endpoint, где произошла ошибка
     */
    public ErrorResponseDTO(int status, String message, String path) {
        this(status, message, LocalDateTime.now(), path, null, null);
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
        this(status, message, LocalDateTime.now(), path, errors, null);
    }

    /**
     * Конструктор со списком ссылающихся записей (для ошибок удаления по FK).
     *
     * @param status     HTTP статус-код
     * @param message    описание ошибки
     * @param path       путь к endpoint, где произошла ошибка
     * @param errors     ошибки по полям формы
     * @param references записи, ссылающиеся на удаляемый объект
     *                   (null, если ошибка не связана с внешним ключом)
     */
    public ErrorResponseDTO(int status, String message, String path, Map<String, String> errors,
                            List<ReferenceDTO> references) {
        this(status, message, LocalDateTime.now(), path, errors, references);
    }
}
