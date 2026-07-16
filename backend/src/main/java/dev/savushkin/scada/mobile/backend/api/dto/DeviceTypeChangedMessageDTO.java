package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменился тип устройства.
 */
public record DeviceTypeChangedMessageDTO(String type, DeviceTypePayload payload, String action) {

    public static final String TYPE = "DEVICE_TYPE_CHANGED";

    public record DeviceTypePayload(Long id, String code, String name) {
    }

    public static DeviceTypeChangedMessageDTO of(DeviceTypePayload payload, String action) {
        return new DeviceTypeChangedMessageDTO(TYPE, payload, action);
    }
}
