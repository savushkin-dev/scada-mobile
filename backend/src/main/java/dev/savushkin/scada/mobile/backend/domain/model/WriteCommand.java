package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Objects;

/**
 * Доменная модель, представляющая команду записи для модуля SCADA.
 * <p>
 * Это чистая доменная модель, которая представляет бизнес-концепцию
 * отправки команды модулю SCADA для выполнения действия. Она независима от:
 * <ul>
 *   <li>Протоколов передачи (PrintSrv, REST)</li>
 *   <li>Механизмов сериализации (JSON, XML)</li>
 *   <li>Зависимостей фреймворков (Spring, Jackson)</li>
 * </ul>
 * <p>
 * Инварианты, обеспечиваемые этим классом:
 * <ul>
 *   <li>Номер модуля должен быть положительным (индексация с 1)</li>
 *   <li>Карта свойств не может быть null или пуста</li>
 *   <li>Временная метка всегда устанавливается и не может быть отрицательной</li>
 * </ul>
 * <p>
 * Этот класс неизменяем и потокобезопасен.
 */
public final class WriteCommand {
    private final long timestamp;
    private final int unitNumber;

    /**
     * Полезная нагрузка команды записи.
     * Сейчас поддерживается один параметр: значение "command" для PrintSrv.
     */
    private final int commandValue;

    /**
     * Создаёт новую команду записи с текущей временной меткой.
     *
     * @param unitNumber номер модуля (индексация с 1, должен быть >= 1)
     * @param commandValue значение команды (то, что раньше хранилось как properties["command"])
     * @throws IllegalArgumentException если нарушены инварианты
     */
    public WriteCommand(int unitNumber, int commandValue) {
        this(System.currentTimeMillis(), unitNumber, commandValue);
    }

    /**
     * Создаёт новую команду записи с явно указанной временной меткой.
     *
     * @param timestamp  временная метка в миллисекундах с начала эпохи
     * @param unitNumber номер модуля (индексация с 1, должен быть >= 1)
     * @param commandValue значение команды (то, что раньше хранилось как properties["command"])
     * @throws IllegalArgumentException если нарушены инварианты
     */
    public WriteCommand(long timestamp, int unitNumber, int commandValue) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative: " + timestamp);
        }
        if (unitNumber < 1) {
            throw new IllegalArgumentException("Unit number must be >= 1, got: " + unitNumber);
        }

        this.timestamp = timestamp;
        this.unitNumber = unitNumber;
        this.commandValue = commandValue;
    }

    /**
     * Возвращает временную метку создания этой команды.
     *
     * @return временная метка в миллисекундах с начала эпохи
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Возвращает номер модуля, для которого предназначена эта команда.
     *
     * @return номер модуля (индексация с 1)
     */
    public int getUnitNumber() {
        return unitNumber;
    }

    /**
     * Возвращает значение команды (то, что раньше хранилось как properties["command"]).
     */
    public int getCommandValue() {
        return commandValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WriteCommand that = (WriteCommand) o;
        return timestamp == that.timestamp
                && unitNumber == that.unitNumber
                && commandValue == that.commandValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, unitNumber, commandValue);
    }

    @Override
    public String toString() {
        return "WriteCommand{" +
                "timestamp=" + timestamp +
                ", unitNumber=" + unitNumber +
                ", commandValue=" + commandValue +
                '}';
    }
}
