package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Map;

/**
 * Доменная модель, представляющая снимок полного состояния устройства SCADA.
 * <p>
 * Это чистая доменная модель, которая захватывает состояние всех модулей устройства
 * в определённый момент времени. Она независима от:
 * <ul>
 *   <li>Протоколов передачи (PrintSrv, REST)</li>
 *   <li>Механизмов сериализации (JSON, XML)</li>
 *   <li>Зависимостей фреймворков (Spring, Jackson)</li>
 * </ul>
 * <p>
 * Инварианты, обеспечиваемые этим классом:
 * <ul>
 *   <li>Имя устройства не может быть null или пусто</li>
 *   <li>Карта модулей не может быть null (но может быть пуста для устройств без модулей)</li>
 * </ul>
 * <p>
 * Этот класс неизменяем и потокобезопасен.
 */
public record DeviceSnapshot(String deviceName, Map<String, UnitSnapshot> units) {
    /**
     * Создаёт новый снимок состояния устройства.
     *
     * @param deviceName имя устройства (не должно быть null или пусто)
     * @param units      карта снимков модулей по ключу модуля (например, "u1", "u2")
     * @throws IllegalArgumentException если нарушены инварианты
     */
    public DeviceSnapshot {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Device name cannot be null or empty");
        }
        if (units == null) {
            throw new IllegalArgumentException("Units map cannot be null");
        }

        // Создание неизменяемой копии для обеспечения потокобезопасности
        units = Map.copyOf(units);
    }

    /**
     * Возвращает количество модулей в этом снимке.
     *
     * @return количество модулей
     */
    public int getUnitCount() {
        return units.size();
    }
}
