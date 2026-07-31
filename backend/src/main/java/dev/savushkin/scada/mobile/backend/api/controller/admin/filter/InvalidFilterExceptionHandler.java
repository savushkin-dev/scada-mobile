package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Превращает ошибки валидации фильтров в структурированный ответ 400
 * с указанием проблемного параметра. Фронтенд показывает его
 * пользователю как «Некорректный фильтр».
 */
@RestControllerAdvice
public class InvalidFilterExceptionHandler {

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFilter(InvalidFilterException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "invalid_filter");
        body.put("message", "Некорректный фильтр: " + ex.getMessage());
        if (ex.getParameter() != null) {
            body.put("parameter", ex.getParameter());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
