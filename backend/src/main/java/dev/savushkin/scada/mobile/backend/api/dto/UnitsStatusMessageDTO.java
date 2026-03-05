package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Конверт WebSocket-сообщения для рассылки статуса аппаратов цеха.
 * <p>
 * Тип {@code "UNITS_STATUS"} позволяет клиенту различать сообщения.
 *
 * <pre>
 * {
 *   "type": "UNITS_STATUS",
 *   "workshopId": "dess",
 *   "payload": [
 *     {"unitId": "hassia2", "workshopId": "dess", "event": "В работе", "timer": "00:00:00"}
 *   ]
 * }
 * </pre>
 */
public record UnitsStatusMessageDTO(
        String type,
        String workshopId,
        List<UnitStatusDTO> payload
) {
    /**
     * Фабричный метод — тип всегда фиксирован.
     */
    @Contract("_, _ -> new")
    public static @NonNull UnitsStatusMessageDTO of(String workshopId, List<UnitStatusDTO> payload) {
        return new UnitsStatusMessageDTO("UNITS_STATUS", workshopId, payload);
    }
}
