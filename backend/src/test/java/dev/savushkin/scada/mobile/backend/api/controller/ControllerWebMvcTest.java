package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.exception.GlobalExceptionHandler;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVC-тесты для Controller (api/v1.0.0).
 * <p>
 * Использует MockMvcBuilders.standaloneSetup() — без загрузки Spring-контекста.
 * Проверяет: HTTP-методы, пути, коды ответов, структуру JSON.
 * <p>
 * Тесты для /workshops и /workshops/{id}/units будут добавлены по мере реализации.
 */
@ExtendWith(MockitoExtension.class)
class ControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private CommandsService commandsService;

    @Mock
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new Controller(commandsService, healthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1.0.0/health/live
    // -------------------------------------------------------------------------

    @Test
    void live_returns200WithStatusUp() throws Exception {
        when(healthService.isAlive()).thenReturn(true);

        mockMvc.perform(get("/api/v1.0.0/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1.0.0/health/ready
    // -------------------------------------------------------------------------

    @Test
    void ready_whenReady_returns200() throws Exception {
        when(healthService.isReady()).thenReturn(true);

        mockMvc.perform(get("/api/v1.0.0/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void ready_whenNotReady_returns503() throws Exception {
        when(healthService.isReady()).thenReturn(false);

        mockMvc.perform(get("/api/v1.0.0/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.ready").value(false));
    }

    // -------------------------------------------------------------------------
    // Неизвестный маршрут → не 2xx
    // -------------------------------------------------------------------------

    @Test
    void unknownRoute_returnsNon2xx() throws Exception {
        mockMvc.perform(get("/api/v1.0.0/doesNotExist"))
                .andExpect(status().is5xxServerError());
    }

    // -------------------------------------------------------------------------
    // TODO: GET /api/v1.0.0/workshops
    // TODO: GET /api/v1.0.0/workshops/{id}/units
    // -------------------------------------------------------------------------
}
