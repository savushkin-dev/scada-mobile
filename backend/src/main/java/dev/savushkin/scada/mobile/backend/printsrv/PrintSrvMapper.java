package dev.savushkin.scada.mobile.backend.printsrv;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.printsrv.dto.PropertiesDTO;
import dev.savushkin.scada.mobile.backend.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.printsrv.dto.UnitsDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapper for converting between PrintSrv protocol DTOs and domain models.
 * <p>
 * This mapper is responsible for translating the external PrintSrv protocol
 * representations into internal domain models. It isolates domain models
 * from changes in the PrintSrv protocol.
 * <p>
 * Direction: PrintSrv DTO → Domain Model
 */
@Component
public class PrintSrvMapper {

    /**
     * Converts a PrintSrv QueryAll response to a domain DeviceSnapshot.
     *
     * @param dto the PrintSrv response DTO
     * @return domain model representing the device snapshot
     */
    public DeviceSnapshot toDomainDeviceSnapshot(QueryAllResponseDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("QueryAllResponseDTO cannot be null");
        }

        Map<String, UnitSnapshot> units = new LinkedHashMap<>();
        
        for (Map.Entry<String, UnitsDTO> entry : dto.units().entrySet()) {
            String unitKey = entry.getKey();
            UnitsDTO unitDto = entry.getValue();
            
            // Extract unit number from key (e.g., "u1" -> 1, "u2" -> 2)
            int unitNumber = extractUnitNumber(unitKey);
            
            UnitSnapshot unitSnapshot = toDomainUnitSnapshot(unitNumber, unitDto);
            units.put(unitKey, unitSnapshot);
        }

        return new DeviceSnapshot(dto.deviceName(), units);
    }

    /**
     * Converts a PrintSrv unit DTO to a domain UnitSnapshot.
     *
     * @param unitNumber the unit number (1-based)
     * @param dto        the PrintSrv unit DTO
     * @return domain model representing the unit snapshot
     */
    public UnitSnapshot toDomainUnitSnapshot(int unitNumber, UnitsDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("UnitsDTO cannot be null");
        }

        UnitProperties properties = toDomainProperties(dto.properties());
        
        return new UnitSnapshot(
                unitNumber,
                dto.state() != null ? dto.state() : "",
                dto.task() != null ? dto.task() : "",
                dto.counter(),
                properties
        );
    }

    /**
     * Converts PrintSrv properties DTO to domain UnitProperties.
     *
     * @param dto the PrintSrv properties DTO
     * @return domain model representing unit properties
     */
    public UnitProperties toDomainProperties(PropertiesDTO dto) {
        if (dto == null) {
            // Return empty properties if DTO is null
            return UnitProperties.builder().build();
        }

        return UnitProperties.builder()
                .command(dto.command())
                .message(dto.message())
                .error(dto.error())
                .errorMessage(dto.errorMessage())
                .cmdSuccess(dto.cmdSuccess())
                .st(dto.st())
                .batchId(dto.batchId())
                .curItem(dto.curItem())
                .batchIdCodesQueue(dto.batchIdCodesQueue())
                .setBatchId(dto.setBatchId())
                .devChangeBatch(dto.devChangeBatch())
                .devsChangeBatchIdQueueControl(dto.devsChangeBatchIdQueueControl())
                .devType(dto.devType())
                .lineId(dto.lineId())
                .onChangeBatchPrinters(dto.onChangeBatchPrinters())
                .level1Printers(dto.level1Printers())
                .level2Printers(dto.level2Printers())
                .onChangeBatchCams(dto.onChangeBatchCams())
                .level1Cams(dto.level1Cams())
                .level2Cams(dto.level2Cams())
                .signalCams(dto.signalCams())
                .lineDevices(dto.lineDevices())
                .enableErrors(dto.enableErrors())
                .build();
    }

    /**
     * Extracts unit number from unit key.
     *
     * @param unitKey the unit key (e.g., "u1", "u2")
     * @return the unit number (1-based)
     * @throws IllegalArgumentException if unit key format is invalid
     */
    private int extractUnitNumber(String unitKey) {
        if (unitKey == null || !unitKey.startsWith("u")) {
            throw new IllegalArgumentException("Invalid unit key format: " + unitKey);
        }
        
        try {
            return Integer.parseInt(unitKey.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid unit key format: " + unitKey, e);
        }
    }
}
