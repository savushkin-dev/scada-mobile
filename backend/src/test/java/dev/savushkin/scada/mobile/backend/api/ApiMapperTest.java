package dev.savushkin.scada.mobile.backend.api;

import dev.savushkin.scada.mobile.backend.api.dto.ChangeCommandResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueryStateResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitPropertiesDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitStateDTO;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для ApiMapper.
 * <p>
 * ApiMapper — чистый компонент без внешних зависимостей (pure function mapper),
 * поэтому тестируется без Spring-контекста и без моков: создаётся напрямую.
 * <p>
 * Стратегия: проверяем контракт маппинга — правильность отображения полей
 * domain → DTO. Mockito здесь не нужен.
 */
class ApiMapperTest {

    // Тестируемый объект — реальный, не мок.
    private ApiMapper apiMapper;

    // Переиспользуемые domain-объекты (набор значений взят из реального снапшота системы).
    private UnitProperties fullProperties;
    private UnitSnapshot fullUnitSnapshot;

    @BeforeEach
    void setUp() {
        apiMapper = new ApiMapper();

        fullProperties = UnitProperties.builder()
                .command(25)
                .message("25")
                .error("0")
                .errorMessage("")
                .cmdSuccess("0")
                .st("0")
                .batchId("")
                .curItem("1605 | 147 | 19.08.2025")
                .batchIdCodesQueue("0")
                .setBatchId("0")
                .devChangeBatch("")
                .devsChangeBatchIdQueueControl("")
                .devType("10")
                .lineId("5")
                .onChangeBatchPrinters("Level1Printers")
                .level1Printers("Printer11,Printer12,Printer13,Printer14")
                .level2Printers("Printer2")
                .onChangeBatchCams("Level1Cams")
                .level1Cams("CamChecker1,CamChecker2,CamAgregation1,CamAgregation2")
                .level2Cams("")
                .signalCams("")
                .lineDevices("CamChecker1,CamChecker2,CamAgregation1,CamAgregation2,CamAgregationBox1,CamAgregationBox2,Printer11,Printer12,Printer13,Printer14")
                .enableErrors("0")
                .build();

        fullUnitSnapshot = new UnitSnapshot(1, "", "", 0, fullProperties);
    }

    // -------------------------------------------------------------------------
    // toApiQueryStateResponse
    // -------------------------------------------------------------------------

    @Test
    void toApiQueryStateResponse_mapsDeviceNameAndUnitKeys() {
        DeviceSnapshot snapshot = new DeviceSnapshot("Line", Map.of("u1", fullUnitSnapshot));

        QueryStateResponseDTO response = apiMapper.toApiQueryStateResponse(snapshot);

        assertEquals("Line", response.deviceName());
        assertNotNull(response.units());
        assertTrue(response.units().containsKey("u1"));
        assertEquals(1, response.units().size());
    }

    @Test
    void toApiQueryStateResponse_mapsUnitStateForEachUnit() {
        DeviceSnapshot snapshot = new DeviceSnapshot("Line", Map.of("u1", fullUnitSnapshot));

        QueryStateResponseDTO response = apiMapper.toApiQueryStateResponse(snapshot);

        UnitStateDTO u1 = response.units().get("u1");
        assertNotNull(u1);
        assertEquals("", u1.state());
        assertEquals("", u1.task());
        assertEquals(0, u1.counter());
        assertNotNull(u1.properties());
    }

    @Test
    void toApiQueryStateResponse_emptyUnits_returnsEmptyMap() {
        // Граничный случай: устройство без units (допустимо по инварианту DeviceSnapshot)
        DeviceSnapshot snapshot = new DeviceSnapshot("Line", Map.of());

        QueryStateResponseDTO response = apiMapper.toApiQueryStateResponse(snapshot);

        assertEquals("Line", response.deviceName());
        assertTrue(response.units().isEmpty());
    }

    // -------------------------------------------------------------------------
    // toApiUnitState
    // -------------------------------------------------------------------------

    @Test
    void toApiUnitState_mapsStateTaskCounter() {
        UnitSnapshot snapshot = new UnitSnapshot(2, "Работа", "Печать", 42, fullProperties);

        UnitStateDTO dto = apiMapper.toApiUnitState(snapshot);

        assertEquals("Работа", dto.state());
        assertEquals("Печать", dto.task());
        assertEquals(42, dto.counter());
    }

    @Test
    void toApiUnitState_mapsProperties() {
        UnitStateDTO dto = apiMapper.toApiUnitState(fullUnitSnapshot);

        assertNotNull(dto.properties());
        assertEquals(25, dto.properties().command());
        assertEquals("25", dto.properties().message());
        assertEquals("0", dto.properties().error());
    }

    @Test
    void toApiUnitState_nullCounter_remainsNull() {
        // counter опционален в domain (может быть null)
        UnitSnapshot snapshot = new UnitSnapshot(1, "", "", null, fullProperties);

        UnitStateDTO dto = apiMapper.toApiUnitState(snapshot);

        assertNull(dto.counter());
    }

    // -------------------------------------------------------------------------
    // toApiUnitProperties
    // -------------------------------------------------------------------------

    @Test
    void toApiUnitProperties_mapsAllPresentFields() {
        UnitPropertiesDTO dto = apiMapper.toApiUnitProperties(fullProperties);

        // Проверяем репрезентативную выборку: начало, середину и конец списка полей —
        // это достаточно, чтобы поймать сдвиг порядка параметров в конструкторе DTO.
        assertEquals(25, dto.command());
        assertEquals("25", dto.message());
        assertEquals("0", dto.error());
        assertEquals("", dto.errorMessage());
        assertEquals("0", dto.cmdSuccess());
        assertEquals("1605 | 147 | 19.08.2025", dto.curItem());
        assertEquals("10", dto.devType());
        assertEquals("5", dto.lineId());
        assertEquals("Printer11,Printer12,Printer13,Printer14", dto.level1Printers());
        assertEquals("CamChecker1,CamChecker2,CamAgregation1,CamAgregation2", dto.level1Cams());
        assertEquals("CamChecker1,CamChecker2,CamAgregation1,CamAgregation2,CamAgregationBox1,CamAgregationBox2,Printer11,Printer12,Printer13,Printer14",
                dto.lineDevices());
        assertEquals("0", dto.enableErrors());
    }

    @Test
    void toApiUnitProperties_absentOptionals_mappedToNull() {
        // Только command задан — все остальные Optional-поля должны стать null в DTO.
        UnitProperties sparseProperties = UnitProperties.builder()
                .command(10)
                .build();

        UnitPropertiesDTO dto = apiMapper.toApiUnitProperties(sparseProperties);

        assertEquals(10, dto.command());
        assertNull(dto.message());
        assertNull(dto.error());
        assertNull(dto.errorMessage());
        assertNull(dto.curItem());
        assertNull(dto.lineId());
        assertNull(dto.enableErrors());
    }

    // -------------------------------------------------------------------------
    // toApiChangeCommandResponse
    // -------------------------------------------------------------------------

    @Test
    void toApiChangeCommandResponse_fixedHeaderFields() {
        ChangeCommandResponseDTO response = apiMapper.toApiChangeCommandResponse(1, 128);

        assertEquals("Line", response.deviceName());
        assertEquals("SetUnitVars", response.command());
    }

    @Test
    void toApiChangeCommandResponse_unitKeyMatchesUnitNumber() {
        ChangeCommandResponseDTO response = apiMapper.toApiChangeCommandResponse(3, 64);

        assertTrue(response.units().containsKey("u3"),
                "Ключ должен быть 'u' + unit number");
        assertEquals(1, response.units().size());
    }

    @Test
    void toApiChangeCommandResponse_onlyCommandFieldIsSet() {
        // Ack-ответ содержит только value в command, остальные поля null —
        // это контракт: не реальное состояние, а подтверждение приёма.
        ChangeCommandResponseDTO response = apiMapper.toApiChangeCommandResponse(1, 128);

        UnitStateDTO u1 = response.units().get("u1");
        assertNotNull(u1);

        // state/task/counter не известны на момент ack
        assertNull(u1.state());
        assertNull(u1.task());
        assertNull(u1.counter());

        assertNotNull(u1.properties());
        assertEquals(128, u1.properties().command());

        // Все остальные property-поля null (только команда передаётся)
        assertNull(u1.properties().message());
        assertNull(u1.properties().error());
        assertNull(u1.properties().lineId());
        assertNull(u1.properties().enableErrors());
    }

    @Test
    void toApiChangeCommandResponse_differentUnitNumbers_produceCorrectKeys() {
        // Проверяем что unit number корректно подставляется в ключ для разных значений
        assertEquals("u1", apiMapper.toApiChangeCommandResponse(1, 1).units().keySet().iterator().next());
        assertEquals("u5", apiMapper.toApiChangeCommandResponse(5, 1).units().keySet().iterator().next());
        assertEquals("u10", apiMapper.toApiChangeCommandResponse(10, 1).units().keySet().iterator().next());
    }
}
