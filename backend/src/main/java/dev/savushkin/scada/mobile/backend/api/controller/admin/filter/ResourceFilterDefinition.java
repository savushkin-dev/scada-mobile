package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Декларативное описание фильтрации для одного ресурса админ-панели:
 * по каким полям работает глобальный поиск и какие поля доступны
 * для структурированных фильтров.
 *
 * @param searchFields пути текстовых полей для глобального поиска (q)
 * @param fields       доступные фильтруемые поля
 */
public record ResourceFilterDefinition(
        List<String> searchFields,
        List<FilterableField> fields
) {
    public Map<String, FilterableField> fieldsByName() {
        return fields.stream().collect(Collectors.toMap(FilterableField::name, Function.identity()));
    }
}
