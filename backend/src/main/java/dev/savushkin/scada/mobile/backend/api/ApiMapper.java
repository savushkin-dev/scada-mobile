package dev.savushkin.scada.mobile.backend.api;

import dev.savushkin.scada.mobile.backend.api.dto.*;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapper for converting between domain models and API DTOs.
 * <p>
 * This mapper is responsible for translating internal domain models
 * into public REST API representations. It isolates the API contract
 * from changes in the domain model.
 * <p>
 * Direction: Domain Model → API DTO
 */
@Component
public class ApiMapper {

    /**
     * Converts a domain DeviceSnapshot to an API QueryStateResponseDTO.
     *
     * @param snapshot the domain device snapshot
     * @return API DTO for the query state response
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
     * Converts a domain UnitSnapshot to an API UnitStateDTO.
     *
     * @param snapshot the domain unit snapshot
     * @return API DTO for the unit state
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
     * Converts domain UnitProperties to an API UnitPropertiesDTO.
     *
     * @param properties the domain unit properties
     * @return API DTO for the unit properties
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
     * Creates an acknowledgment response for a command change.
     *
     * @param unit  the unit number
     * @param value the command value
     * @return API DTO for the change command response
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
