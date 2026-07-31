package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.List;

/**
 * Построитель предиката для полей типа {@link FilterFieldType#CUSTOM}.
 * Используется, когда фильтр нельзя выразить путём атрибута
 * (например, EXISTS-подзапрос по связанной коллекции — без дублей строк
 * и без влияния на пагинацию).
 */
@FunctionalInterface
public interface CustomFilterPredicate {

    /**
     * Строит предикат фильтра.
     *
     * @param root   корень основного запроса
     * @param query  основной запрос (для создания подзапросов)
     * @param cb     criteria builder
     * @param values сырые строковые значения фильтра (при eq — один элемент)
     */
    Predicate toPredicate(Root<?> root, CriteriaQuery<?> query, CriteriaBuilder cb, List<String> values);
}
