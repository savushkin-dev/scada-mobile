package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

import org.jspecify.annotations.Nullable;

/**
 * Описание одного фильтруемого поля сущности.
 *
 * @param name      имя поля в контракте API (параметр запроса {@code f.<name>})
 * @param type      тип поля, определяет допустимые операторы и парсинг значения
 * @param path      путь к атрибуту сущности; вложенные связи задаются через точку
 *                  (например, {@code "role.id"} или {@code "catalog.type.id"});
 *                  для {@link FilterFieldType#CUSTOM} не используется
 * @param enumClass класс enum для полей типа {@link FilterFieldType#ENUM}, иначе null
 * @param custom    построитель предиката для полей типа {@link FilterFieldType#CUSTOM}, иначе null
 */
public record FilterableField(
        String name,
        FilterFieldType type,
        String path,
        @Nullable Class<? extends Enum<?>> enumClass,
        @Nullable CustomFilterPredicate custom
) {
    public static FilterableField text(String name, String path) {
        return new FilterableField(name, FilterFieldType.TEXT, path, null, null);
    }

    public static FilterableField number(String name, String path) {
        return new FilterableField(name, FilterFieldType.NUMBER, path, null, null);
    }

    public static FilterableField bool(String name, String path) {
        return new FilterableField(name, FilterFieldType.BOOLEAN, path, null, null);
    }

    public static FilterableField dateTime(String name, String path) {
        return new FilterableField(name, FilterFieldType.DATE_TIME, path, null, null);
    }

    public static FilterableField ofEnum(String name, String path, Class<? extends Enum<?>> enumClass) {
        return new FilterableField(name, FilterFieldType.ENUM, path, enumClass, null);
    }

    /** Поле с собственным предикатом (EXISTS-подзапрос и т.п.). */
    public static FilterableField custom(String name, CustomFilterPredicate predicate) {
        return new FilterableField(name, FilterFieldType.CUSTOM, "", null, predicate);
    }
}
