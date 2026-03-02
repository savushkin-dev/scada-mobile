package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.UnitsDTO;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsDTO;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopServiceTest {

    @Mock
    private InstanceSnapshotRepository snapshotRepo;

    private WorkshopService service;

    @BeforeEach
    void setUp() {
        PrintSrvProperties config = new PrintSrvProperties();

        PrintSrvProperties.WorkshopProperties ws1 = new PrintSrvProperties.WorkshopProperties();
        ws1.setId("dess");
        ws1.setDisplayName("Цех десертов");

        PrintSrvProperties.WorkshopProperties ws2 = new PrintSrvProperties.WorkshopProperties();
        ws2.setId("pouring");
        ws2.setDisplayName("Цех розлива");

        config.setWorkshops(List.of(ws1, ws2));

        PrintSrvProperties.InstanceProperties inst1 = new PrintSrvProperties.InstanceProperties();
        inst1.setId("trepko1");
        inst1.setDisplayName("Trepko №1");
        inst1.setWorkshopId("dess");

        PrintSrvProperties.InstanceProperties inst2 = new PrintSrvProperties.InstanceProperties();
        inst2.setId("hassia2");
        inst2.setDisplayName("Hassia №2");
        inst2.setWorkshopId("dess");

        PrintSrvProperties.InstanceProperties inst3 = new PrintSrvProperties.InstanceProperties();
        inst3.setId("bosch1");
        inst3.setDisplayName("Bosch №1");
        inst3.setWorkshopId("pouring");

        config.setInstances(List.of(inst1, inst2, inst3));

        service = new WorkshopService(config, snapshotRepo);
    }

    @Test
    void getWorkshops_returnsTwoWorkshopsWithCorrectCounts() {
        List<WorkshopsDTO> workshops = service.getWorkshops();

        assertEquals(2, workshops.size());
        assertEquals("dess", workshops.get(0).id());
        assertEquals("Цех десертов", workshops.get(0).name());
        assertEquals(2, workshops.get(0).totalUnits());
        assertEquals("pouring", workshops.get(1).id());
        assertEquals(1, workshops.get(1).totalUnits());
    }

    @Test
    void getWorkshops_countsProblemsFromSnapshots() {
        // trepko1 has error
        DeviceSnapshot errorSnapshot = createLineSnapshot("1");
        when(snapshotRepo.get("trepko1", "Line")).thenReturn(errorSnapshot);

        // hassia2 has no error
        DeviceSnapshot okSnapshot = createLineSnapshot("0");
        when(snapshotRepo.get("hassia2", "Line")).thenReturn(okSnapshot);

        List<WorkshopsDTO> workshops = service.getWorkshops();
        assertEquals(1, workshops.get(0).problemUnits());
    }

    @Test
    void getUnits_returnsUnitsForWorkshop() {
        List<UnitsDTO> units = service.getUnits("dess");

        assertEquals(2, units.size());
        assertEquals("trepko1", units.get(0).id());
        assertEquals("dess", units.get(0).workshopId());
        assertEquals("Trepko №1", units.get(0).unit());
    }

    @Test
    void getUnits_unknownWorkshop_returnsEmptyList() {
        List<UnitsDTO> units = service.getUnits("unknown");
        assertTrue(units.isEmpty());
    }

    @Test
    void getUnits_eventDerivedFromSnapshot() {
        DeviceSnapshot running = createLineSnapshotWithSt("1");
        when(snapshotRepo.get("trepko1", "Line")).thenReturn(running);

        List<UnitsDTO> units = service.getUnits("dess");
        assertEquals("В работе", units.get(0).event());
    }

    @Test
    void getUnits_eventIsNoDataWhenNoSnapshot() {
        when(snapshotRepo.get("trepko1", "Line")).thenReturn(null);

        List<UnitsDTO> units = service.getUnits("dess");
        assertEquals("Нет данных", units.get(0).event());
    }

    @Test
    void workshopExists_returnsCorrectly() {
        assertTrue(service.workshopExists("dess"));
        assertTrue(service.workshopExists("pouring"));
        assertFalse(service.workshopExists("unknown"));
    }

    private DeviceSnapshot createLineSnapshot(String error) {
        UnitProperties props = UnitProperties.builder().error(error).st("0").build();
        UnitSnapshot unit = new UnitSnapshot(1, "0", "", null, props);
        return new DeviceSnapshot("Line", Map.of("u1", unit));
    }

    private DeviceSnapshot createLineSnapshotWithSt(String st) {
        UnitProperties props = UnitProperties.builder().error("0").st(st).build();
        UnitSnapshot unit = new UnitSnapshot(1, st, "", null, props);
        return new DeviceSnapshot("Line", Map.of("u1", unit));
    }
}
