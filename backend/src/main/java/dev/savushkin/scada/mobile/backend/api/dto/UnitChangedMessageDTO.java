package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменились данные автомата (unit).
 */
public record UnitChangedMessageDTO(String type, UnitPayload payload, String action) {

    public static final String TYPE = "UNIT_CHANGED";

    public record UnitPayload(Long id, String printsrvInstanceId, Long workshopId, String name, boolean active) {
    }

    public static UnitChangedMessageDTO of(UnitPayload payload, String action) {
        return new UnitChangedMessageDTO(TYPE, payload, action);
    }
}
