package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменились данные сотрудника.
 */
public record EmployeeChangedMessageDTO(String type, EmployeePayload payload, String action) {

    public static final String TYPE = "EMPLOYEE_CHANGED";

    public record EmployeePayload(Long id, String fullName, String code, Long roleId, boolean active) {
    }

    public static EmployeeChangedMessageDTO of(EmployeePayload payload, String action) {
        return new EmployeeChangedMessageDTO(TYPE, payload, action);
    }
}
