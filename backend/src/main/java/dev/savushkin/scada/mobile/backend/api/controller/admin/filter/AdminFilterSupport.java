package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationSeverity;
import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Единая точка входа серверной фильтрации админ-панели.
 * <p>
 * Держит декларативный реестр фильтруемых полей по ресурсам,
 * валидирует входные параметры и строит JPA {@link Specification}.
 * <p>
 * Контракт: {@code q} — глобальный поиск по текстовым полям;
 * {@code f.<field>} — структурированный фильтр (см. {@link ParsedFilters}).
 * Неизвестное поле, недопустимый оператор или значение несовместимого
 * типа приводят к {@link InvalidFilterException} с указанием параметра.
 */
public final class AdminFilterSupport {

    private AdminFilterSupport() {
    }

    // ── Реестр ресурсов ───────────────────────────────────────────────────

    private static final Map<String, ResourceFilterDefinition> REGISTRY = Map.of(
            "roles", new ResourceFilterDefinition(
                    List.of("name"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.text("name", "name")
                    )),
            "workshops", new ResourceFilterDefinition(
                    List.of("name"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.text("name", "name"),
                            FilterableField.bool("active", "active")
                    )),
            "device-types", new ResourceFilterDefinition(
                    List.of("code", "name"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.text("code", "code"),
                            FilterableField.text("name", "name")
                    )),
            "units", new ResourceFilterDefinition(
                    List.of("name", "printsrvInstanceId", "printsrvHost"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.text("name", "name"),
                            FilterableField.text("printsrvInstanceId", "printsrvInstanceId"),
                            FilterableField.text("printsrvHost", "printsrvHost"),
                            FilterableField.number("printsrvPort", "printsrvPort"),
                            FilterableField.bool("active", "active"),
                            FilterableField.number("workshopId", "workshop.id")
                    )),
            "device-catalog", new ResourceFilterDefinition(
                    List.of("code", "name"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.text("code", "code"),
                            FilterableField.text("name", "name"),
                            FilterableField.bool("active", "active"),
                            FilterableField.number("typeId", "type.id")
                    )),
            "devices", new ResourceFilterDefinition(
                    List.of("catalog.code", "catalog.name"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.text("code", "catalog.code"),
                            FilterableField.text("displayName", "catalog.name"),
                            FilterableField.number("unitId", "unit.id"),
                            FilterableField.number("catalogId", "catalog.id"),
                            FilterableField.number("typeId", "catalog.type.id")
                    )),
            "users", new ResourceFilterDefinition(
                    List.of("code", "fullName"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.text("code", "code"),
                            FilterableField.text("fullName", "fullName"),
                            FilterableField.bool("active", "active"),
                            FilterableField.number("roleId", "role.id")
                    )),
            "notifications", new ResourceFilterDefinition(
                    List.of("instanceId", "deviceCode", "message"),
                    List.of(
                            FilterableField.number("id", "id"),
                            FilterableField.ofEnum("type", "type", AdminNotificationType.class),
                            FilterableField.ofEnum("severity", "severity", AdminNotificationSeverity.class),
                            FilterableField.text("instanceId", "instanceId"),
                            FilterableField.text("deviceCode", "deviceCode"),
                            FilterableField.text("message", "message"),
                            FilterableField.bool("read", "read"),
                            FilterableField.dateTime("createdAt", "createdAt")
                    ))
    );

    /** Есть ли у ресурса описание фильтрации. */
    public static boolean supports(String resource) {
        return REGISTRY.containsKey(resource);
    }

    // ── Построение Specification ──────────────────────────────────────────

    /**
     * Строит Specification из query-параметров запроса.
     *
     * @param resource имя ресурса (как в URL: roles, units, device-catalog, ...)
     * @param params   все query-параметры запроса; используются {@code q} и {@code f.*}
     */
    public static <T> Specification<T> specification(String resource, Map<String, String> params) {
        ResourceFilterDefinition definition = REGISTRY.get(resource);
        if (definition == null) {
            throw new InvalidFilterException(null, "Фильтрация не поддерживается для ресурса " + resource);
        }
        ParsedFilters parsed = ParsedFilters.fromParams(params);
        return buildSpecification(definition, parsed);
    }

    private static <T> Specification<T> buildSpecification(ResourceFilterDefinition definition,
                                                           ParsedFilters parsed) {
        Map<String, FilterableField> fields = definition.fieldsByName();

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (parsed.query() != null && !parsed.query().isBlank() && !definition.searchFields().isEmpty()) {
                String pattern = likePattern(parsed.query());
                List<Predicate> searchPredicates = new ArrayList<>();
                for (String path : definition.searchFields()) {
                    Expression<String> expr = resolve(root, path).as(String.class);
                    searchPredicates.add(cb.like(cb.lower(expr), pattern));
                }
                predicates.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
            }

            for (ParsedFilters.FieldFilter filter : parsed.filters()) {
                FilterableField field = fields.get(filter.field());
                if (field == null) {
                    throw new InvalidFilterException("f." + filter.field(),
                            "Неизвестное поле фильтра. Допустимые поля: " + String.join(", ", fields.keySet()));
                }
                predicates.add(toPredicate(root, cb, field, filter));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ── Предикаты по типам ────────────────────────────────────────────────

    private static Predicate toPredicate(jakarta.persistence.criteria.Root<?> root,
                                         CriteriaBuilder cb,
                                         FilterableField field,
                                         ParsedFilters.FieldFilter filter) {
        String param = "f." + field.name();
        Expression<?> expr = resolve(root, field.path());

        return switch (field.type()) {
            case TEXT -> {
                requireOps(param, filter, ParsedFilters.Op.EQ, ParsedFilters.Op.IN);
                List<Predicate> likes = filter.values().stream()
                        .map(v -> cb.like(cb.lower(expr.as(String.class)), likePattern(v)))
                        .toList();
                yield likes.size() == 1 ? likes.getFirst() : cb.or(likes.toArray(Predicate[]::new));
            }
            case BOOLEAN -> {
                requireOps(param, filter, ParsedFilters.Op.EQ, ParsedFilters.Op.IN);
                List<Boolean> values = filter.values().stream()
                        .map(v -> parseBoolean(param, v))
                        .toList();
                yield expr.as(Boolean.class).in(values);
            }
            case ENUM -> {
                requireOps(param, filter, ParsedFilters.Op.EQ, ParsedFilters.Op.IN);
                List<Enum<?>> values = new ArrayList<>();
                for (String v : filter.values()) {
                    values.add(parseEnum(param, field.enumClass(), v));
                }
                yield expr.in(values);
            }
            case NUMBER -> numberPredicate(cb, expr, filter, param);
            case DATE_TIME -> dateTimePredicate(cb, expr, filter, param);
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate numberPredicate(CriteriaBuilder cb,
                                             Expression<?> expr,
                                             ParsedFilters.FieldFilter filter,
                                             String param) {
        Class<?> javaType = expr.getJavaType();
        List<? extends Number> numbers = filter.values().stream()
                .map(v -> parseNumber(param, javaType, v))
                .toList();

        Expression<? extends Comparable> comparable = (Expression<? extends Comparable>) expr;
        return switch (filter.op()) {
            case EQ -> cb.equal(expr, numbers.getFirst());
            case IN -> expr.in(numbers);
            case GT -> cb.greaterThan(comparable, (Comparable) numbers.getFirst());
            case LT -> cb.lessThan(comparable, (Comparable) numbers.getFirst());
            case GTE -> cb.greaterThanOrEqualTo(comparable, (Comparable) numbers.getFirst());
            case LTE -> cb.lessThanOrEqualTo(comparable, (Comparable) numbers.getFirst());
            case BETWEEN -> cb.between(comparable, (Comparable) numbers.get(0), (Comparable) numbers.get(1));
        };
    }

    private static Predicate dateTimePredicate(CriteriaBuilder cb,
                                               Expression<?> expr,
                                               ParsedFilters.FieldFilter filter,
                                               String param) {
        Expression<LocalDateTime> dateTime = expr.as(LocalDateTime.class);
        return switch (filter.op()) {
            case EQ -> {
                DateTimeBound bound = parseDateTimeBound(param, filter.values().getFirst(), false);
                if (bound.wholeDay()) {
                    yield cb.between(dateTime, bound.value(), bound.value().plusDays(1).minusNanos(1));
                }
                yield cb.equal(dateTime, bound.value());
            }
            case GT -> cb.greaterThan(dateTime, parseDateTimeBound(param, filter.values().getFirst(), true).value());
            case LT -> cb.lessThan(dateTime, parseDateTimeBound(param, filter.values().getFirst(), false).value());
            case GTE -> cb.greaterThanOrEqualTo(dateTime, parseDateTimeBound(param, filter.values().getFirst(), false).value());
            case LTE -> cb.lessThanOrEqualTo(dateTime, parseDateTimeBound(param, filter.values().getFirst(), true).value());
            case BETWEEN -> cb.between(dateTime,
                    parseDateTimeBound(param, filter.values().get(0), false).value(),
                    parseDateTimeBound(param, filter.values().get(1), true).value());
            case IN -> throw new InvalidFilterException(param,
                    "Список значений не поддерживается для полей даты/времени; используйте between:мин,макс");
        };
    }

    // ── Парсинг значений ──────────────────────────────────────────────────

    private static void requireOps(String param, ParsedFilters.FieldFilter filter,
                                   ParsedFilters.Op... allowed) {
        for (ParsedFilters.Op op : allowed) {
            if (filter.op() == op) return;
        }
        throw new InvalidFilterException(param,
                "Оператор " + filter.op().name().toLowerCase() + " недопустим для этого поля. Допустимые: "
                        + Arrays.stream(allowed).map(o -> o.name().toLowerCase()).collect(Collectors.joining(", ")));
    }

    private static String likePattern(String value) {
        return "%" + value.toLowerCase() + "%";
    }

    private static Boolean parseBoolean(String param, String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new InvalidFilterException(param, "Ожидается булево значение (true/false), получено: " + value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> parseEnum(String param, @Nullable Class<? extends Enum<?>> enumClass, String value) {
        if (enumClass == null) {
            throw new InvalidFilterException(param, "Поле не является перечислением");
        }
        try {
            return Enum.valueOf((Class<? extends Enum>) enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            String allowed = Arrays.stream(enumClass.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new InvalidFilterException(param,
                    "Недопустимое значение '" + value + "'. Допустимые значения: " + allowed);
        }
    }

    private static Number parseNumber(String param, Class<?> javaType, String value) {
        try {
            if (Integer.class.equals(javaType) || int.class.equals(javaType)) {
                return Integer.parseInt(value);
            }
            if (Long.class.equals(javaType) || long.class.equals(javaType)) {
                return Long.parseLong(value);
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new InvalidFilterException(param, "Ожидается число, получено: " + value);
        }
    }

    private record DateTimeBound(LocalDateTime value, boolean wholeDay) {
    }

    /**
     * Парсит дату ({@code yyyy-MM-dd}) или дату-время (ISO).
     *
     * @param endOfDay для даты без времени: true — конец дня, false — начало дня
     */
    private static DateTimeBound parseDateTimeBound(String param, String value, boolean endOfDay) {
        try {
            if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(value);
                return new DateTimeBound(date.atTime(endOfDay ? LocalTime.MAX : LocalTime.MIN), true);
            }
            return new DateTimeBound(LocalDateTime.parse(value), false);
        } catch (java.time.format.DateTimeParseException e) {
            throw new InvalidFilterException(param,
                    "Ожидается дата (yyyy-MM-dd) или дата-время (ISO), получено: " + value);
        }
    }

    // ── Разрешение путей атрибутов ────────────────────────────────────────

    /** Разрешает путь вида {@code "catalog.type.id"} через LEFT JOIN промежуточных связей. */
    private static Expression<?> resolve(jakarta.persistence.criteria.Root<?> root, String path) {
        String[] parts = path.split("\\.");
        From<?, ?> from = root;
        for (int i = 0; i < parts.length - 1; i++) {
            from = from.join(parts[i], JoinType.LEFT);
        }
        return from.get(parts[parts.length - 1]);
    }
}
