package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.ChangeCommandResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueryStateResponseDTO;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandsControllerTest {

    @Mock
    private CommandsService commandsService;
    @Mock
    private HealthService healthService;

    @InjectMocks
    private CommandsController commandsController;

    @Test
    void queryAll() {
        QueryStateResponseDTO dto = new QueryStateResponseDTO("Line", Map.of());
        when(commandsService.queryAll()).thenReturn(dto);

        ResponseEntity<QueryStateResponseDTO> response = commandsController.queryAll();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
        verify(commandsService, times(1)).queryAll();
    }

    @Test
    void setUnitVars() {
        int unit = 1;
        int value = 128;

        ChangeCommandResponseDTO dto = new ChangeCommandResponseDTO("Line", "SetUnitVars", Map.of());
        when(commandsService.setUnitVars(unit, value)).thenReturn(dto);

        ResponseEntity<ChangeCommandResponseDTO> response = commandsController.setUnitVars(unit, value);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
        verify(commandsService, times(1)).setUnitVars(unit, value);
    }

    @Test
    void live() {
        when(healthService.isAlive()).thenReturn(true);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        CommandsController controllerWithFixedClock = new CommandsController(commandsService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = controllerWithFixedClock.live();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of(
                "status", "UP",
                "timestamp", fixedInstant.toString()
        ), response.getBody());

        verify(healthService, times(1)).isAlive();
        verifyNoInteractions(commandsService);
    }

    @Test
    void ready() {
        when(healthService.isReady()).thenReturn(true);

        Instant fixedInstant = Instant.parse("2026-02-23T12:34:56Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        CommandsController controllerWithFixedClock = new CommandsController(commandsService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = controllerWithFixedClock.ready();

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

        CommandsController controllerWithFixedClock = new CommandsController(commandsService, healthService, fixedClock);

        ResponseEntity<Map<String, Object>> response = controllerWithFixedClock.ready();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(Map.of(
                "status", "DOWN",
                "timestamp", fixedInstant.toString(),
                "ready", false
        ), response.getBody());

        verify(healthService, times(1)).isReady();
        verifyNoInteractions(commandsService);
    }
}
