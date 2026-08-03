package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * DTO с информацией о записи, ссылающейся на удаляемый объект.
 * <p>
 * Используется в {@link ErrorResponseDTO#references()} при ошибке удаления
 * записи, на которую есть ссылки по внешнему ключу (FK-violation, SQLState 23503),
 * чтобы клиент мог показать пользователю, кто именно ссылается на запись.
 *
 * @param type      машинное имя ресурса в стиле роутов админки
 *                  (users, units, device-catalog, user-assignments,
 *                  user-notification-settings, unit-devices)
 * @param typeLabel русское название ресурса во множественном числе
 *                  («Сотрудники», «Автоматы», «Каталог устройств»,
 *                  «Привязки автоматов», «Настройки уведомлений», «Устройства»)
 * @param id        идентификатор ссылающейся записи
 * @param name      человекочитаемое отображаемое имя записи
 */
public record ReferenceDTO(
        String type,
        String typeLabel,
        Long id,
        String name
) {
}
