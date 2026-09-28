package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import org.jspecify.annotations.NonNull;

/**
 * Дефолты per-unit раскладки для новой связи unit_devices.
 * Единая точка для auto-discovery и ручного добавления устройства в админке.
 */
public final class DeviceLayoutDefaults {

    private DeviceLayoutDefaults() {
        // utility class
    }

    /**
     * Заполняет дефолты раскладки: display_order в конец списка, show_counters
     * и scada_prefix по правилам {@link ScadaKeyMapper} (если тип известен).
     *
     * @param device       новая связь
     * @param catalog      запись справочника
     * @param displayOrder количество уже существующих связей у аппарата
     * @param indexInType  количество существующих связей того же типа у аппарата
     */
    public static void apply(@NonNull DeviceEntity device, @NonNull DeviceCatalogEntity catalog,
                             long displayOrder, long indexInType) {
        device.setDisplayOrder((int) displayOrder);
        String typeCode = catalog.getType() != null ? catalog.getType().getCode() : null;
        if (typeCode == null) {
            return;
        }
        device.setShowCounters(ScadaKeyMapper.defaultShowCounters(typeCode, catalog.getCode()));
        device.setScadaPrefix(ScadaKeyMapper.defaultPrefix(typeCode, catalog.getCode(), (int) indexInType));
    }
}
