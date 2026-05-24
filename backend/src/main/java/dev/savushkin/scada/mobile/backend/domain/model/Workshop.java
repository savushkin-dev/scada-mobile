package dev.savushkin.scada.mobile.backend.domain.model;

import org.jspecify.annotations.NonNull;

/**
 * Доменная модель цеха — агрегированное представление данных из БД.
 *
 * @param id           внутренний ID (workshops.workshop_id)
 * @param displayName  отображаемое название (workshops.name)
 */
public record Workshop(
        long id,
        @NonNull String displayName
) {
}
