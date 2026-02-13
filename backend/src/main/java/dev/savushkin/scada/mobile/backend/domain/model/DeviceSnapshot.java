package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Map;
import java.util.Objects;

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
public final class DeviceSnapshot {
    private final String deviceName;
    private final Map<String, UnitSnapshot> units;

    /**
     * Создаёт новый снимок состояния устройства.
     *
     * @param deviceName имя устройства (не должно быть null или пусто)
     * @param units      карта снимков модулей по ключу модуля (например, "u1", "u2")
     * @throws IllegalArgumentException если нарушены инварианты
     */
    public DeviceSnapshot(String deviceName, Map<String, UnitSnapshot> units) {
        if (deviceName == null || deviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Device name cannot be null or empty");
        }
        if (units == null) {
            throw new IllegalArgumentException("Units map cannot be null");
        }

        this.deviceName = deviceName;
        // Создание неизменяемой копии для обеспечения потокобезопасности
        this.units = Map.copyOf(units);
    }

    /**
     * Возвращает имя устройства.
     *
     * @return имя устройства (никогда не null или пусто)
     */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * Возвращает карту снимков модулей.
     *
     * @return неизменяемая карта модулей (никогда не null, но может быть пуста)
     */
    public Map<String, UnitSnapshot> getUnits() {
        return units;
    }

    /**
     * Возвращает снимок конкретного модуля.
     *
     * @param unitKey ключ модуля (например, "u1", "u2")
     * @return снимок модуля или null, если не найден
     */
    public UnitSnapshot getUnit(String unitKey) {
        return units.get(unitKey);
    }

    /**
     * Проверяет, содержит ли этот снимок конкретный модуль.
     *
     * @param unitKey ключ модуля для проверки
     * @return true, если модуль существует в этом снимке
     */
    public boolean hasUnit(String unitKey) {
        return units.containsKey(unitKey);
    }

    /**
     * Возвращает количество модулей в этом снимке.
     *
     * @return количество модулей
     */
    public int getUnitCount() {
        return units.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeviceSnapshot that = (DeviceSnapshot) o;
        return deviceName.equals(that.deviceName)
                && units.equals(that.units);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceName, units);
    }

    @Override
    public String toString() {
        return "DeviceSnapshot{" +
                "deviceName='" + deviceName + '\'' +
                ", units=" + units.keySet() +
                ", unitCount=" + units.size() +
                '}';
    }
}
