package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.PropertiesDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.UnitsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты для PrintSrvMapper.
 * Проверяет преобразование PrintSrv DTO → domain model:
 * - toDomainDeviceSnapshot
 * - toDomainUnitSnapshot
 * - toDomainProperties (включая null DTO)
 * - extractUnitNumber (через публичный метод)
 */
class PrintSrvMapperTest {

    private PrintSrvMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PrintSrvMapper();
    }

    // -------------------------------------------------------------------------
    // toDomainDeviceSnapshot
    // -------------------------------------------------------------------------

    @Test
    void toDomainDeviceSnapshot_mapsDeviceName() {
        QueryAllResponseDTO dto = buildQueryAllDto("Line", Map.of());

        DeviceSnapshot snap = mapper.toDomainDeviceSnapshot(dto);

        assertEquals("Line", snap.deviceName());
    }

    @Test
    void toDomainDeviceSnapshot_emptyUnits_returnsEmptyMap() {
        QueryAllResponseDTO dto = buildQueryAllDto("Line", Map.of());

        DeviceSnapshot snap = mapper.toDomainDeviceSnapshot(dto);

        assertTrue(snap.units().isEmpty());
    }

    @Test
    void toDomainDeviceSnapshot_mapsAllUnits() {
        QueryAllResponseDTO dto = buildQueryAllDto("Line", Map.of(
                "u1", buildUnit("Running", "Print", 10, 25),
                "u2", buildUnit("Idle", "Wait", null, null)
        ));

        DeviceSnapshot snap = mapper.toDomainDeviceSnapshot(dto);

        assertEquals(2, snap.getUnitCount());
        assertTrue(snap.units().containsKey("u1"));
        assertTrue(snap.units().containsKey("u2"));
    }

    @Test
    void toDomainDeviceSnapshot_mapsUnitNumber_fromKey() {
        QueryAllResponseDTO dto = buildQueryAllDto("Line", Map.of(
                "u3", buildUnit("Running", "Print", 0, 10)
        ));

        DeviceSnapshot snap = mapper.toDomainDeviceSnapshot(dto);

        UnitSnapshot u3 = snap.units().get("u3");
        assertNotNull(u3);
        assertEquals(3, u3.unitNumber());
    }

    @Test
    void toDomainDeviceSnapshot_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toDomainDeviceSnapshot(null));
    }

    // -------------------------------------------------------------------------
    // toDomainUnitSnapshot
    // -------------------------------------------------------------------------

    @Test
    void toDomainUnitSnapshot_mapsStateTaskCounter() {
        UnitsDTO dto = buildUnit("Running", "LabelPrint", 42, 128);

        UnitSnapshot snap = mapper.toDomainUnitSnapshot(1, dto);

        assertEquals(1, snap.unitNumber());
        assertEquals("Running", snap.state());
        assertEquals("LabelPrint", snap.task());
        assertEquals(42, snap.counter());
    }

    @Test
    void toDomainUnitSnapshot_nullState_mappedToEmpty() {
        UnitsDTO dto = new UnitsDTO(null, "task", 1, buildProps(10));

        UnitSnapshot snap = mapper.toDomainUnitSnapshot(1, dto);

        assertEquals("", snap.state());
    }

    @Test
    void toDomainUnitSnapshot_nullTask_mappedToEmpty() {
        UnitsDTO dto = new UnitsDTO("state", null, 1, buildProps(10));

        UnitSnapshot snap = mapper.toDomainUnitSnapshot(1, dto);

        assertEquals("", snap.task());
    }

    @Test
    void toDomainUnitSnapshot_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toDomainUnitSnapshot(1, null));
    }

    // -------------------------------------------------------------------------
    // toDomainProperties — null DTO → empty props
    // -------------------------------------------------------------------------

    @Test
    void toDomainProperties_nullDto_returnsEmptyProperties() {
        var props = mapper.toDomainProperties(null);

        assertTrue(props.getCommand().isEmpty());
        assertTrue(props.getMessage().isEmpty());
    }

    @Test
    void toDomainProperties_fullDto_mapsAllFields() {
        PropertiesDTO dto = new PropertiesDTO(
                99, "Msg", "E1", "EMsg", "1", "ST", "BID",
                "CI", "BCQ", "SBI", "DCB", "DCBQC", "10", "5",
                "OCBPrinters", "L1P", "L2P", "OCBCams", "L1C", "L2C",
                "SC", "LD", "0"
        );

        var props = mapper.toDomainProperties(dto);

        assertEquals(99, props.getCommand().get());
        assertEquals("Msg", props.getMessage().get());
        assertEquals("E1", props.getError().get());
        assertEquals("EMsg", props.getErrorMessage().get());
        assertEquals("10", props.getDevType().get());
        assertEquals("5", props.getLineId().get());
        assertEquals("0", props.getEnableErrors().get());
    }

    // -------------------------------------------------------------------------
    // Некорректный ключ unit
    // -------------------------------------------------------------------------

    @Test
    void toDomainDeviceSnapshot_invalidUnitKey_throwsIllegalArgument() {
        QueryAllResponseDTO dto = buildQueryAllDto("Line", Map.of(
                "x1", buildUnit("s", "t", 0, 1)
        ));

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toDomainDeviceSnapshot(dto));
    }

    @Test
    void toDomainDeviceSnapshot_unitKeyNonNumericSuffix_throwsIllegalArgument() {
        QueryAllResponseDTO dto = buildQueryAllDto("Line", Map.of(
                "uABC", buildUnit("s", "t", 0, 1)
        ));

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toDomainDeviceSnapshot(dto));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static QueryAllResponseDTO buildQueryAllDto(String deviceName, Map<String, UnitsDTO> units) {
        return new QueryAllResponseDTO(deviceName, "QueryAll", units);
    }

    private static UnitsDTO buildUnit(String state, String task, Integer counter, Integer command) {
        return new UnitsDTO(state, task, counter, buildProps(command));
    }

    private static PropertiesDTO buildProps(Integer command) {
        return new PropertiesDTO(
                command, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );
    }
}
