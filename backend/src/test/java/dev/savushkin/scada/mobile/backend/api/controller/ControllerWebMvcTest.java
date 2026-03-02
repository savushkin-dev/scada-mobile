package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.UnitsDTO_new;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsDTO_new;
import dev.savushkin.scada.mobile.backend.exception.GlobalExceptionHandler;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private WorkshopService workshopService;

    @Mock
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new Controller(workshopService, healthService))
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
    // GET /api/workshops
    // -------------------------------------------------------------------------

    @Test
    void getWorkshops_returns200WithList() throws Exception {
        when(workshopService.getWorkshops()).thenReturn(List.of(
                new WorkshopsDTO_new("dess", "Цех десертов", 7, 2),
                new WorkshopsDTO_new("dess_pouring", "Цех десертов и розлива", 7, 0)
        ));

        mockMvc.perform(get("/api/workshops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("dess"))
                .andExpect(jsonPath("$[0].totalUnits").value(7))
                .andExpect(jsonPath("$[1].id").value("dess_pouring"));
    }

    // -------------------------------------------------------------------------
    // GET /api/workshops/{id}/units
    // -------------------------------------------------------------------------

    @Test
    void getUnits_existingWorkshop_returns200() throws Exception {
        when(workshopService.workshopExists("dess")).thenReturn(true);
        when(workshopService.getUnits("dess")).thenReturn(List.of(
                new UnitsDTO_new("trepko1", "dess", "Trepko №1", "В работе", "00:00:00")
        ));

        mockMvc.perform(get("/api/workshops/dess/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("trepko1"))
                .andExpect(jsonPath("$[0].unit").value("Trepko №1"));
    }

    @Test
    void getUnits_unknownWorkshop_returns404() throws Exception {
        when(workshopService.workshopExists("unknown")).thenReturn(false);

        mockMvc.perform(get("/api/workshops/unknown/units"))
                .andExpect(status().isNotFound());
    }
}
