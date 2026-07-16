package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменились данные цеха.
 */
public record WorkshopChangedMessageDTO(String type, WorkshopPayload payload, String action) {

    public static final String TYPE = "WORKSHOP_CHANGED";

    public record WorkshopPayload(Long id, String name, boolean active, int totalUnits) {
    }

    public static WorkshopChangedMessageDTO of(WorkshopPayload payload, String action) {
        return new WorkshopChangedMessageDTO(TYPE, payload, action);
    }
}
