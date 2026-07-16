package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил данные сотрудника.
 */
public record EmployeeChangedEvent(Long employeeId, ChangeAction action) {
}
