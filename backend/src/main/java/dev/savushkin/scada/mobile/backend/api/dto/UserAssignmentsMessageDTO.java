package dev.savushkin.scada.mobile.backend.api.dto;

import java.util.List;

/**
 * WebSocket-сообщение с актуальным списком аппаратов, закреплённых за пользователем.
 * <p>
 * Отправляется персонально сотруднику при изменении его назначений администратором.
 */
public record UserAssignmentsMessageDTO(
        String type,
        List<AssignedUnitPayload> payload
) {
    public record AssignedUnitPayload(
            long unitId,
            String printsrvInstanceId,
            String unitName
    ) {
    }

    public static UserAssignmentsMessageDTO of(List<AssignedUnitPayload> payload) {
        return new UserAssignmentsMessageDTO("USER_ASSIGNMENTS", payload);
    }
}
