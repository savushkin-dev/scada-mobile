package dev.savushkin.scada.mobile.backend.api;

import dev.savushkin.scada.mobile.backend.api.dto.*;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Маппер для преобразования между доменными моделями и API DTOs.
 * <p>
 * Этот маппер отвечает за трансляцию внутренних доменных моделей
 * в представления публичного REST API. Он изолирует контракт API
 * от изменений в доменной модели.
 * <p>
 * Направление: Доменная модель → API DTO
 */
@Component
public class ApiMapper {

    /**
     * Преобразует доменный DeviceSnapshot в API QueryStateResponseDTO.
     *
     * @param snapshot снимок состояния устройства доменной модели
     * @return API DTO для ответа запроса состояния
     */
    public QueryStateResponseDTO toApiQueryStateResponse(DeviceSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("DeviceSnapshot cannot be null");
        }

        Map<String, UnitStateDTO> units = new LinkedHashMap<>();

        for (Map.Entry<String, UnitSnapshot> entry : snapshot.getUnits().entrySet()) {
            String unitKey = entry.getKey();
            UnitSnapshot unitSnapshot = entry.getValue();

            UnitStateDTO unitStateDto = toApiUnitState(unitSnapshot);
            units.put(unitKey, unitStateDto);
        }

        return new QueryStateResponseDTO(snapshot.getDeviceName(), units);
    }

    /**
     * Преобразует доменный UnitSnapshot в API UnitStateDTO.
     *
     * @param snapshot снимок состояния модуля доменной модели
     * @return API DTO для состояния модуля
     */
    public UnitStateDTO toApiUnitState(UnitSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("UnitSnapshot cannot be null");
        }

        UnitPropertiesDTO propertiesDto = toApiUnitProperties(snapshot.getProperties());

        return new UnitStateDTO(
                snapshot.getState(),
                snapshot.getTask(),
                snapshot.getCounter(),
                propertiesDto
        );
    }

    /**
     * Преобразует доменные UnitProperties в API UnitPropertiesDTO.
     *
     * @param properties свойства модуля доменной модели
     * @return API DTO для свойств модуля
     */
    public UnitPropertiesDTO toApiUnitProperties(UnitProperties properties) {
        if (properties == null) {
            return null;
        }

        return new UnitPropertiesDTO(
                properties.getCommand().orElse(null),
                properties.getMessage().orElse(null),
                properties.getError().orElse(null),
                properties.getErrorMessage().orElse(null),
                properties.getCmdSuccess().orElse(null),
                properties.getSt().orElse(null),
                properties.getBatchId().orElse(null),
                properties.getCurItem().orElse(null),
                properties.getBatchIdCodesQueue().orElse(null),
                properties.getSetBatchId().orElse(null),
                properties.getDevChangeBatch().orElse(null),
                properties.getDevsChangeBatchIdQueueControl().orElse(null),
                properties.getDevType().orElse(null),
                properties.getLineId().orElse(null),
                properties.getOnChangeBatchPrinters().orElse(null),
                properties.getLevel1Printers().orElse(null),
                properties.getLevel2Printers().orElse(null),
                properties.getOnChangeBatchCams().orElse(null),
                properties.getLevel1Cams().orElse(null),
                properties.getLevel2Cams().orElse(null),
                properties.getSignalCams().orElse(null),
                properties.getLineDevices().orElse(null),
                properties.getEnableErrors().orElse(null)
        );
    }

    /**
     * Создаёт ответ подтверждения для изменения команды.
     *
     * @param unit  номер модуля
     * @param value значение команды
     * @return API DTO для ответа на изменение команды
     */
    public ChangeCommandResponseDTO toApiChangeCommandResponse(int unit, int value) {
        UnitPropertiesDTO properties = new UnitPropertiesDTO(
                value, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );

        UnitStateDTO unitState = new UnitStateDTO(
                null, // state - not known yet
                null, // task - not known yet
                null, // counter - not known yet
                properties
        );

        return new ChangeCommandResponseDTO(
                "Line",
                "SetUnitVars",
                Map.of("u" + unit, unitState)
        );
    }
}
