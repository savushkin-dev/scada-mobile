package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменилась связь устройства с автоматом (unit_devices).
 */
public record DeviceChangedMessageDTO(String type, DevicePayload payload, String action) {

    public static final String TYPE = "DEVICE_CHANGED";

    public record DevicePayload(Long id, Long unitId, String printsrvInstanceId, Long catalogId) {
    }

    public static DeviceChangedMessageDTO of(DevicePayload payload, String action) {
        return new DeviceChangedMessageDTO(TYPE, payload, action);
    }
}
