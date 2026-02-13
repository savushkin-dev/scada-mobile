package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Objects;

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
public final class UnitSnapshot {
    private final int unitNumber;
    private final String state;
    private final String task;
    private final Integer counter;
    private final UnitProperties properties;

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
    public UnitSnapshot(int unitNumber, String state, String task, Integer counter, UnitProperties properties) {
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

        this.unitNumber = unitNumber;
        this.state = state;
        this.task = task;
        this.counter = counter;
        this.properties = properties;
    }

    /**
     * Возвращает номер модуля.
     *
     * @return номер модуля (индексация с 1)
     */
    public int getUnitNumber() {
        return unitNumber;
    }

    /**
     * Возвращает текущее состояние модуля.
     *
     * @return состояние (никогда не null)
     */
    public String getState() {
        return state;
    }

    /**
     * Возвращает текущую задачу модуля.
     *
     * @return задача (никогда не null)
     */
    public String getTask() {
        return task;
    }

    /**
     * Возвращает счётчик операций.
     *
     * @return счётчик или null, если недоступен
     */
    public Integer getCounter() {
        return counter;
    }

    /**
     * Возвращает свойства модуля.
     *
     * @return свойства (никогда не null)
     */
    public UnitProperties getProperties() {
        return properties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitSnapshot that = (UnitSnapshot) o;
        return unitNumber == that.unitNumber
                && state.equals(that.state)
                && task.equals(that.task)
                && Objects.equals(counter, that.counter)
                && properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unitNumber, state, task, counter, properties);
    }

    @Override
    public String toString() {
        return "UnitSnapshot{" +
                "unitNumber=" + unitNumber +
                ", state='" + state + '\'' +
                ", task='" + task + '\'' +
                ", counter=" + counter +
                ", properties=" + properties +
                '}';
    }
}
