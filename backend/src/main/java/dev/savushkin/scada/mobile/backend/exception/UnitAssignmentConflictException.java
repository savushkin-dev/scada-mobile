package dev.savushkin.scada.mobile.backend.exception;

import org.jspecify.annotations.NonNull;

/**
 * Исключение конфликта при закреплении автомата за сотрудником.
 * <p>
 * Возникает, когда попытка назначить автомат сотруднику нарушает
 * правило 1:1 (автомат уже активно закреплён за другим сотрудником).
 */
public class UnitAssignmentConflictException extends RuntimeException {

    private final String field;

    public UnitAssignmentConflictException(@NonNull String field, @NonNull String message) {
        super(message);
        this.field = field;
    }

    public @NonNull String getField() {
        return field;
    }
}
