package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControllerTest {

    @Mock
    private CommandsService commandsService;
    @Mock
    private HealthService healthService;

    // Контроллер создаётся в каждом тесте с фиксированными Clock для детерминированности timestamp.

    // -------------------------------------------------------------------------
    // GET /api/v1.0.0/health/live
    // -------------------------------------------------------------------------

    @Test
    void live_returnsOk_withStatusUp() {
        when(healthService.isAlive()).thenReturn(true);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        Controller c = new Controller(commandsService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = c.live();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of(
                "status", "UP",
                "timestamp", fixedInstant.toString()
        ), response.getBody());
        verify(healthService, times(1)).isAlive();
        verifyNoInteractions(commandsService);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1.0.0/health/ready
    // -------------------------------------------------------------------------

    @Test
    void ready_whenReady_returnsOk() {
        when(healthService.isReady()).thenReturn(true);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        Controller c = new Controller(commandsService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = c.ready();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of(
                "status", "UP",
                "timestamp", fixedInstant.toString(),
                "ready", true
        ), response.getBody());
        verify(healthService, times(1)).isReady();
        verifyNoInteractions(commandsService);
    }

    @Test
    void ready_whenNotReady_returns503() {
        when(healthService.isReady()).thenReturn(false);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        Controller c = new Controller(commandsService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = c.ready();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of(
                "status", "DOWN",
                "timestamp", fixedInstant.toString(),
                "ready", false
        ), response.getBody());
        verify(healthService, times(1)).isReady();
        verifyNoInteractions(commandsService);
    }

    // -------------------------------------------------------------------------
    // TODO: GET /api/v1.0.0/workshops
    // TODO: GET /api/v1.0.0/workshops/{id}/units
    // -------------------------------------------------------------------------
}
