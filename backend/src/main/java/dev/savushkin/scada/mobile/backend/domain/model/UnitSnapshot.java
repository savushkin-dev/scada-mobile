package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменная модель, представляющая снимок состояния модуля SCADA в определённый момент времени.
 * <p>
 * Это чистая доменная модель, которая захватывает суть операционного состояния модуля.
 * Она независима от:
 * <ul>
 *   <li>Протоколов передачи (PrintSrv, REST)</li>
 *   <li>Механизмов сериализации (JSON, XML)</li>
 *   <li>Зависимостей фреймворков (Spring, Jackson)</li>
 * </ul>
 * <p>
 * Инварианты, обеспечиваемые этим классом:
 * <ul>
 *   <li>Номер модуля должен быть положительным (индексация с 1)</li>
 *   <li>Состояние, задача и свойства не могут быть null (используйте пустые значения, если не применимо)</li>
 * </ul>
 * <p>
 * Этот класс неизменяем и потокобезопасен.
 */
public record UnitSnapshot(int unitNumber, String state, String task, Integer counter, UnitProperties properties) {
    /**
     * Создаёт новый снимок состояния модуля.
     *
     * @param unitNumber номер модуля (индексация с 1, должен быть >= 1)
     * @param state      текущее состояние модуля (не должно быть null)
     * @param task       текущая задача модуля (не должна быть null)
     * @param counter    счётчик операций (может быть null для некоторых операций)
     * @param properties свойства модуля (не должны быть null)
     * @throws IllegalArgumentException если нарушены инварианты
     */
    public UnitSnapshot {
        if (unitNumber < 1) {
            throw new IllegalArgumentException("Unit number must be >= 1, got: " + unitNumber);
        }
        if (state == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }
    }
}
