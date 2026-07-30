package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

/**
 * Тип фильтруемого поля. Определяет допустимые операторы и способ парсинга значения.
 */
public enum FilterFieldType {
    /** Строковое поле: eq трактуется как «содержит» (LIKE, без учёта регистра). */
    TEXT,
    /** Числовое поле (Long/Integer): eq, gt, lt, gte, lte, between, список значений (OR). */
    NUMBER,
    /** Булево поле: только eq / список значений. */
    BOOLEAN,
    /** Enum-поле: значение — имя константы, допустимы списки (OR). */
    ENUM,
    /** Дата/время (LocalDateTime): eq, gt, lt, gte, lte, between. */
    DATE_TIME
}
