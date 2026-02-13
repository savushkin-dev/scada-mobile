package dev.savushkin.scada.mobile.backend.domain.model;

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
 *
 * @param commandValue Полезная нагрузка команды записи.
 *                     Сейчас поддерживается один параметр: значение "command" для PrintSrv.
 */
public record WriteCommand(long timestamp, int unitNumber, int commandValue) {
    /**
     * Создаёт новую команду записи с текущей временной меткой.
     *
     * @param unitNumber   номер модуля (индексация с 1, должен быть >= 1)
     * @param commandValue значение команды (то, что раньше хранилось как properties["command"])
     * @throws IllegalArgumentException если нарушены инварианты
     */
    public WriteCommand(int unitNumber, int commandValue) {
        this(System.currentTimeMillis(), unitNumber, commandValue);
    }

    /**
     * Создаёт новую команду записи с явно указанной временной меткой.
     *
     * @param timestamp    временная метка в миллисекундах с начала эпохи
     * @param unitNumber   номер модуля (индексация с 1, должен быть >= 1)
     * @param commandValue значение команды (то, что раньше хранилось как properties["command"])
     * @throws IllegalArgumentException если нарушены инварианты
     */
    public WriteCommand {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp cannot be negative: " + timestamp);
        }
        if (unitNumber < 1) {
            throw new IllegalArgumentException("Unit number must be >= 1, got: " + unitNumber);
        }
    }
}
