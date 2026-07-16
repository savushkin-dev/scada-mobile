package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Сообщение WebSocket: изменился справочник устройств (device_catalog).
 */
public record DeviceCatalogChangedMessageDTO(String type, DeviceCatalogPayload payload, String action) {

    public static final String TYPE = "DEVICE_CATALOG_CHANGED";

    public record DeviceCatalogPayload(Long id, String code, String name, Long typeId, boolean active) {
    }

    public static DeviceCatalogChangedMessageDTO of(DeviceCatalogPayload payload, String action) {
        return new DeviceCatalogChangedMessageDTO(TYPE, payload, action);
    }
}
