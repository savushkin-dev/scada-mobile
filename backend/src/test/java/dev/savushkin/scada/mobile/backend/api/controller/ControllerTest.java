package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.UnitsDTO_new;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsDTO_new;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControllerTest {

    @Mock
    private WorkshopService workshopService;
    @Mock
    private HealthService healthService;

    // -------------------------------------------------------------------------
    // GET /api/v1.0.0/health/live
    // -------------------------------------------------------------------------

    @Test
    void live_returnsOk_withStatusUp() {
        when(healthService.isAlive()).thenReturn(true);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        Controller c = new Controller(workshopService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = c.live();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of(
                "status", "UP",
                "timestamp", fixedInstant.toString()
        ), response.getBody());
        verify(healthService, times(1)).isAlive();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1.0.0/health/ready
    // -------------------------------------------------------------------------

    @Test
    void ready_whenReady_returnsOk() {
        when(healthService.isReady()).thenReturn(true);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        Controller c = new Controller(workshopService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = c.ready();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of(
                "status", "UP",
                "timestamp", fixedInstant.toString(),
                "ready", true
        ), response.getBody());
        verify(healthService, times(1)).isReady();
    }

    @Test
    void ready_whenNotReady_returns503() {
        when(healthService.isReady()).thenReturn(false);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        Controller c = new Controller(workshopService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = c.ready();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of(
                "status", "DOWN",
                "timestamp", fixedInstant.toString(),
                "ready", false
        ), response.getBody());
    }

    // -------------------------------------------------------------------------
    // GET /api/workshops
    // -------------------------------------------------------------------------

    @Test
    void getWorkshops_returnsOkWithList() {
        List<WorkshopsDTO_new> workshops = List.of(
                new WorkshopsDTO_new("dess", "Цех десертов", 7, 2),
                new WorkshopsDTO_new("dess_pouring", "Цех десертов и розлива", 7, 0)
        );
        when(workshopService.getWorkshops()).thenReturn(workshops);

        Controller c = new Controller(workshopService, healthService);
        ResponseEntity<List<WorkshopsDTO_new>> response = c.getWorkshops();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("dess", response.getBody().get(0).id());
    }

    // -------------------------------------------------------------------------
    // GET /api/workshops/{id}/units
    // -------------------------------------------------------------------------

    @Test
    void getUnitsInWorkshop_existingWorkshop_returnsOk() {
        when(workshopService.workshopExists("dess")).thenReturn(true);
        List<UnitsDTO_new> units = List.of(
                new UnitsDTO_new("trepko1", "dess", "Trepko №1", "В работе", "00:00:00")
        );
        when(workshopService.getUnits("dess")).thenReturn(units);

        Controller c = new Controller(workshopService, healthService);
        ResponseEntity<List<UnitsDTO_new>> response = c.getUnitsInWorkshop("dess");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getUnitsInWorkshop_unknownWorkshop_returns404() {
        when(workshopService.workshopExists("unknown")).thenReturn(false);

        Controller c = new Controller(workshopService, healthService);
        ResponseEntity<List<UnitsDTO_new>> response = c.getUnitsInWorkshop("unknown");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
