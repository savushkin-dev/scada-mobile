package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменная запись одной активной ошибки устройства.
 *
 * <p>Хранится в {@code UnitErrorStore} и используется двумя независимыми
 * потребителями для обеспечения единого источника правды:
 * <ul>
 *   <li>{@code AlertService} — формирует {@code ALERT} для канала {@code /ws/live};
 *       наличие хотя бы одной записи → карточка аппарата окрашивается в красный.</li>
 *   <li>{@code UnitDetailService} — формирует {@code ERRORS} для канала {@code /ws/unit/{unitId}};
 *       те же записи отображаются во вкладке «Журнал».</li>
 * </ul>
 *
 * <p>Запись намеренно не содержит поля {@code value} — она представляет
 * <b>только активные</b> ошибки (поле уже было отфильтровано на этапе извлечения).
 *
 * @param objectName   Префиксная часть ключа из устройства {@code scada}, идентифицирующая
 *                     конкретное устройство-источник (例 {@code "Dev041"}).
 * @param propertyDesc Полный ключ свойства из снапшота {@code scada}
 *                     (例 {@code "Dev041Dublicate"}).
 * @param description  Человекочитаемое описание ошибки
 *                     (例 {@code "Одинаковые коды маркировки"}).
 */
public record DeviceError(String objectName, String propertyDesc, String description) {
}
