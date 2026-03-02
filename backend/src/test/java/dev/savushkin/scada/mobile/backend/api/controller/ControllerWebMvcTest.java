package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.UnitsDTO;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsDTO;
import dev.savushkin.scada.mobile.backend.exception.GlobalExceptionHandler;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(Controller.class)
@Import(GlobalExceptionHandler.class)
class ControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkshopService workshopService;

    @MockBean
    private HealthService healthService;

    @Value("${scada.api.base-path}")
    private String apiBasePath;

    // -------------------------------------------------------------------------
    // GET ${scada.api.base-path}/health/live
    // -------------------------------------------------------------------------

    @Test
    void live_returns200WithStatusUp() throws Exception {
        when(healthService.isAlive()).thenReturn(true);

        mockMvc.perform(get(apiBasePath + "/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // -------------------------------------------------------------------------
    // GET ${scada.api.base-path}/health/ready
    // -------------------------------------------------------------------------

    @Test
    void ready_whenReady_returns200() throws Exception {
        when(healthService.isReady()).thenReturn(true);

        mockMvc.perform(get(apiBasePath + "/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void ready_whenNotReady_returns503() throws Exception {
        when(healthService.isReady()).thenReturn(false);

        mockMvc.perform(get(apiBasePath + "/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.ready").value(false));
    }

    // -------------------------------------------------------------------------
    // GET ${scada.api.base-path}/workshops
    // -------------------------------------------------------------------------

    @Test
    void getWorkshops_returns200WithList() throws Exception {
        when(workshopService.getWorkshops()).thenReturn(List.of(
                new WorkshopsDTO("dess", "Цех десертов", 7, 2),
                new WorkshopsDTO("dess_pouring", "Цех десертов и розлива", 7, 0)
        ));

        mockMvc.perform(get(apiBasePath + "/workshops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("dess"))
                .andExpect(jsonPath("$[0].totalUnits").value(7))
                .andExpect(jsonPath("$[1].id").value("dess_pouring"));
    }

    // -------------------------------------------------------------------------
    // GET ${scada.api.base-path}/workshops/{id}/units
    // -------------------------------------------------------------------------

    @Test
    void getUnits_existingWorkshop_returns200() throws Exception {
        when(workshopService.workshopExists("dess")).thenReturn(true);
        when(workshopService.getUnits("dess")).thenReturn(List.of(
                new UnitsDTO("trepko1", "dess", "Trepko №1", "В работе", "00:00:00")
        ));

        mockMvc.perform(get(apiBasePath + "/workshops/dess/units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("trepko1"))
                .andExpect(jsonPath("$[0].unit").value("Trepko №1"));
    }

    @Test
    void getUnits_unknownWorkshop_returns404() throws Exception {
        when(workshopService.workshopExists("unknown")).thenReturn(false);

        mockMvc.perform(get(apiBasePath + "/workshops/unknown/units"))
                .andExpect(status().isNotFound());
    }
}
