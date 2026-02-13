package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.PropertiesDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.UnitsDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Маппер для преобразования между PrintSrv DTO и доменными моделями.
 * <p>
 * Этот маппер отвечает за трансляцию внешних представлений протокола PrintSrv
 * во внутренние доменные модели. Он изолирует доменные модели
 * от изменений в протоколе PrintSrv.
 * <p>
 * Направление: PrintSrv DTO → Доменная модель
 */
@Component
public class PrintSrvMapper {

    /**
     * Преобразует ответ PrintSrv QueryAll в доменную DeviceSnapshot.
     *
     * @param dto PrintSrv DTO ответа
     * @return доменная модель, представляющая снимок состояния устройства
     */
    public DeviceSnapshot toDomainDeviceSnapshot(QueryAllResponseDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("QueryAllResponseDTO cannot be null");
        }

        Map<String, UnitSnapshot> units = new LinkedHashMap<>();

        for (Map.Entry<String, UnitsDTO> entry : dto.units().entrySet()) {
            String unitKey = entry.getKey();
            UnitsDTO unitDto = entry.getValue();

            // Извлечение номера модуля из ключа (например, "u1" -> 1, "u2" -> 2)
            int unitNumber = extractUnitNumber(unitKey);

            UnitSnapshot unitSnapshot = toDomainUnitSnapshot(unitNumber, unitDto);
            units.put(unitKey, unitSnapshot);
        }

        return new DeviceSnapshot(dto.deviceName(), units);
    }

    /**
     * Преобразует PrintSrv DTO модуля в доменную UnitSnapshot.
     *
     * @param unitNumber номер модуля (индексация с 1)
     * @param dto        PrintSrv DTO модуля
     * @return доменная модель, представляющая снимок состояния модуля
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
     * Преобразует PrintSrv DTO свойств в доменные UnitProperties.
     *
     * @param dto PrintSrv DTO свойств
     * @return доменная модель, представляющая свойства модуля
     */
    public UnitProperties toDomainProperties(PropertiesDTO dto) {
        if (dto == null) {
            // Возвращение пустых свойств, если DTO равно null
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
     * Извлекает номер модуля из ключа модуля.
     *
     * @param unitKey ключ модуля (например, "u1", "u2")
     * @return номер модуля (индексация с 1)
     * @throws IllegalArgumentException если формат ключа модуля неверный
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
