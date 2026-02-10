package dev.savushkin.scada.mobile.backend.store;

import java.util.Map;

/**
 * Immutable record для представления команды записи в PrintSrv.
 * <p>
 * Команда живет только до следующего scan cycle (до 5 секунд).
 * После обработки команда удаляется из буфера независимо от успеха/неудачи записи.
 * <p>
 * Архитектурный принцип: Last-Write-Wins.
 * Если для одного unit приходит несколько команд до следующего цикла,
 * сохраняется только последняя команда.
 *
 * @param timestamp  время создания команды в миллисекундах (System.currentTimeMillis)
 * @param unit       номер юнита (1-based: 1=u1, 2=u2, etc.)
 * @param properties изменяемые свойства (например, {"command": 999})
 */
public record PendingWriteCommand(
        long timestamp,
        int unit,
        Map<String, Object> properties
) {
    /**
     * Конструктор с автоматической генерацией timestamp.
     *
     * @param unit       номер юнита (1-based)
     * @param properties изменяемые свойства
     */
    public PendingWriteCommand(int unit, Map<String, Object> properties) {
        this(System.currentTimeMillis(), unit, Map.copyOf(properties));
    }

    /**
     * Canonical constructor с валидацией и защитной копией.
     */
    public PendingWriteCommand {
        if (unit < 1) {
            throw new IllegalArgumentException("Unit must be >= 1, got: " + unit);
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties cannot be null or empty");
        }
        // Создаем immutable копию для thread-safety
        properties = Map.copyOf(properties);
    }
}
