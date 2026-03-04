package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Статическая топология цеха ({@code GET /api/v1.0.0/workshops/topology}).
 * <p>
 * Содержит только данные, которые меняются при изменении конфигурации
 * (добавление/переименование цеха, изменение состава аппаратов).
 * Используется для кэширования на стороне клиента.
 * <p>
 * Подготовлен под ETag-кэширование: заголовок {@code ETag} вычисляется
 * один раз при старте из SHA-256 конфигурации (см. {@link dev.savushkin.scada.mobile.backend.services.WorkshopService#getConfigETag()}).
 *
 * @param id         уникальный идентификатор цеха
 * @param name       отображаемое название цеха
 * @param totalUnits общее число аппаратов/линий
 */
public record WorkshopTopologyDTO(
        String id,
        String name,
        int totalUnits
) {
}
