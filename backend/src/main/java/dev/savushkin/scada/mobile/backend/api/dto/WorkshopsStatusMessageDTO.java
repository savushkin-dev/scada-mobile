package dev.savushkin.scada.mobile.backend.api.dto;

import java.util.List;

/**
 * Конверт WebSocket-сообщения для рассылки статуса цехов.
 * <p>
 * Тип {@code "WORKSHOPS_STATUS"} позволяет клиенту различать сообщения
 * без привязки к конкретному каналу.
 *
 * <pre>
 * {
 *   "type": "WORKSHOPS_STATUS",
 *   "payload": [
 *     {"workshopId": "dess", "problemUnits": 2},
 *     {"workshopId": "cheese", "problemUnits": 0}
 *   ]
 * }
 * </pre>
 */
public record WorkshopsStatusMessageDTO(
        String type,
        List<WorkshopStatusDTO> payload
) {
    /**
     * Фабричный метод — тип всегда фиксирован.
     */
    public static WorkshopsStatusMessageDTO of(List<WorkshopStatusDTO> payload) {
        return new WorkshopsStatusMessageDTO("WORKSHOPS_STATUS", payload);
    }
}
