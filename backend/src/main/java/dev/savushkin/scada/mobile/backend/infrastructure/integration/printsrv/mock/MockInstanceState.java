package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Потокобезопасное in-memory состояние одного инстанса PrintSrv.
 *
 * <p>Структура: {@code deviceName → (propertyKey → propertyValue)}.
 * Все значения — строки, как в реальном протоколе PrintSrv.
 *
 * <p>Стратегия блокировки: {@link ReentrantReadWriteLock} на уровне инстанса.
 * <ul>
 *   <li>Чтение {@link #getPropertiesCopy(String)} захватывает read-lock — многopоточное чтение без блокировки.</li>
 *   <li>Запись ({@code setProperty}, {@code mergeProperties}, {@code incrementInt}) — write-lock,
 *       исключительный доступ. Это важно для атомарных read-modify-write циклов в симуляторе.</li>
 * </ul>
 *
 * <p>Изоляция гарантируется архитектурно: каждый инстанс получает свой экземпляр
 * {@code MockInstanceState} при старте реестра. Общего состояния между инстансами нет.
 */
public class MockInstanceState {

    private final String instanceId;

    /**
     * deviceName → properties.
     *
     * <p>Внешний map — ConcurrentHashMap для безопасного добавления устройств.
     * Внутренний map (properties) — обычный HashMap, защищённый через rwLock.
     */
    private final ConcurrentHashMap<String, Map<String, String>> devices = new ConcurrentHashMap<>();

    /**
     * Один lock на весь инстанс: минимизирует сложность по сравнению с lock-per-device,
     * достаточен для частоты тиков симулятора (раз в несколько секунд).
     */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public MockInstanceState(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * Инициализирует устройство начальным набором свойств из XML-файла.
     *
     * <p>Вызывается только при старте реестра; потоки симулятора и polling к этому
     * времени ещё не запущены, поэтому блокировка здесь избыточна, но добавлена
     * для корректности при будущих рефакторингах.
     *
     * @param deviceName имя устройства (например, {@code "Line"})
     * @param properties начальные свойства из XML
     */
    public void initDevice(String deviceName, Map<String, String> properties) {
        rwLock.writeLock().lock();
        try {
            // Копируем, чтобы внешний код не мог изменить наше состояние напрямую
            devices.put(deviceName, new HashMap<>(properties));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Возвращает защитную копию текущих свойств устройства под read-lock.
     *
     * <p>Копия нужна, чтобы вызывающий код мог итерироваться по ней без риска
     * {@link java.util.ConcurrentModificationException}, пока симулятор пишет.
     *
     * @param deviceName имя устройства
     * @return неизменяемый снапшот свойств; пустой map, если устройства нет
     */
    public Map<String, String> getPropertiesCopy(String deviceName) {
        rwLock.readLock().lock();
        try {
            Map<String, String> props = devices.get(deviceName);
            return props != null ? Collections.unmodifiableMap(new HashMap<>(props)) : Map.of();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Устанавливает одно свойство устройства под write-lock.
     */
    public void setProperty(String deviceName, String key, String value) {
        rwLock.writeLock().lock();
        try {
            devices.computeIfAbsent(deviceName, k -> new HashMap<>()).put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Мержит набор свойств в устройство под write-lock (семантика "last-write-wins").
     *
     * <p>Только переданные ключи обновляются,
     * остальные свойства остаются без изменений.
     */
    public void mergeProperties(String deviceName, Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        rwLock.writeLock().lock();
        try {
            devices.computeIfAbsent(deviceName, k -> new HashMap<>()).putAll(updates);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Атомарно увеличивает целочисленное свойство на {@code delta} под write-lock.
     *
     * <p>Если текущее значение не является числом или свойство отсутствует,
     * считается что текущее значение равно 0. Не кидает исключений.
     *
     * @param deviceName имя устройства
     * @param key        имя свойства (например, {@code "Total"}, {@code "BatchSucceeded"})
     * @param delta      приращение; может быть отрицательным
     */
    public void incrementInt(String deviceName, String key, int delta) {
        rwLock.writeLock().lock();
        try {
            Map<String, String> props = devices.computeIfAbsent(deviceName, k -> new HashMap<>());
            String current = props.getOrDefault(key, "0");
            int parsed;
            try {
                parsed = Integer.parseInt(current.trim());
            } catch (NumberFormatException e) {
                // Не число — сбрасываем в 0 и прибавляем delta
                parsed = 0;
            }
            props.put(key, String.valueOf(parsed + delta));
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Проверяет, содержит ли инстанс указанное устройство (инициализировано ли оно).
     */
    public boolean hasDevice(String deviceName) {
        return devices.containsKey(deviceName);
    }

    /**
     * Возвращает список имён устройств, инициализированных в этом инстансе.
     */
    public java.util.Set<String> getDeviceNames() {
        return Collections.unmodifiableSet(devices.keySet());
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public String toString() {
        return "MockInstanceState{instanceId='%s', devices=%s}".formatted(instanceId, devices.keySet());
    }
}
