package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

import org.jspecify.annotations.Nullable;

/**
 * Ошибка валидации параметров фильтрации.
 * Содержит имя проблемного параметра и человекочитаемое описание.
 */
public class InvalidFilterException extends RuntimeException {

    private final @Nullable String parameter;

    public InvalidFilterException(@Nullable String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }

    public @Nullable String getParameter() {
        return parameter;
    }
}
