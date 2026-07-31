package dev.savushkin.scada.mobile.backend.api.controller.admin.filter;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Результат разбора параметров фильтрации.
 *
 * @param query   глобальная поисковая строка (q), null если не задана
 * @param filters структурированные фильтры по полям
 */
public record ParsedFilters(
        @Nullable String query,
        List<FieldFilter> filters
) {
    public boolean isEmpty() {
        return (query == null || query.isBlank()) && filters.isEmpty();
    }

    /** Оператор сравнения. IN — несколько значений через запятую (OR внутри поля). */
    public enum Op {
        EQ, GT, LT, GTE, LTE, BETWEEN, IN
    }

    /**
     * Один структурированный фильтр.
     *
     * @param field  имя поля из контракта (часть после {@code f.})
     * @param op     оператор сравнения
     * @param values значения (1 для одиночных операторов, 2 для BETWEEN, N для IN)
     */
    public record FieldFilter(String field, Op op, List<String> values) {
    }

    /**
     * Разбирает query-параметры запроса: {@code q} — глобальный поиск,
     * {@code f.<field>} — структурированный фильтр.
     * <p>
     * Грамматика значения:
     * <ul>
     *   <li>{@code значение} — eq (для текстовых полей трактуется как «содержит»)</li>
     *   <li>{@code значение1,значение2} — IN (логика OR внутри поля)</li>
     *   <li>{@code gt|lt|gte|lte:значение} — сравнение (числа и даты)</li>
     *   <li>{@code between:мин,макс} — диапазон (числа и даты)</li>
     * </ul>
     * Служебные параметры (page, size, sort и т.п.) игнорируются.
     */
    public static ParsedFilters fromParams(Map<String, String> params) {
        String query = null;
        List<FieldFilter> filters = new java.util.ArrayList<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String raw = entry.getValue();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if ("q".equals(key)) {
                query = raw.trim();
                continue;
            }
            if (!key.startsWith("f.") || key.length() <= 2) {
                continue;
            }
            filters.add(parseFieldFilter(key.substring(2), raw.trim()));
        }
        return new ParsedFilters(query, filters);
    }

    private static FieldFilter parseFieldFilter(String field, String raw) {
        String param = "f." + field;

        int colon = raw.indexOf(':');
        if (colon > 0) {
            String prefix = raw.substring(0, colon).toLowerCase();
            String rest = raw.substring(colon + 1);
            Op op = switch (prefix) {
                case "eq" -> Op.EQ;
                case "gt" -> Op.GT;
                case "lt" -> Op.LT;
                case "gte" -> Op.GTE;
                case "lte" -> Op.LTE;
                case "between" -> Op.BETWEEN;
                default -> null;
            };
            if (op != null) {
                if (rest.isBlank()) {
                    throw new InvalidFilterException(param, "Пустое значение фильтра");
                }
                List<String> values = splitValues(rest);
                if (op == Op.BETWEEN && values.size() != 2) {
                    throw new InvalidFilterException(param,
                            "Оператор between требует два значения через запятую: between:мин,макс");
                }
                if (op != Op.BETWEEN && values.size() != 1) {
                    throw new InvalidFilterException(param,
                            "Оператор " + prefix + " требует одно значение");
                }
                return new FieldFilter(field, op, values);
            }
            // Префикс не является оператором — значение с двоеточием обрабатывается как обычное.
        }

        List<String> values = splitValues(raw);
        return new FieldFilter(field, values.size() > 1 ? Op.IN : Op.EQ, values);
    }

    private static List<String> splitValues(String raw) {
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
