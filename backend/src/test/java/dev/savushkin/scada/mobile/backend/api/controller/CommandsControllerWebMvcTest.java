package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.ChangeCommandResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueryStateResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitPropertiesDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UnitStateDTO;
import dev.savushkin.scada.mobile.backend.config.JacksonConfig;
import dev.savushkin.scada.mobile.backend.exception.GlobalExceptionHandler;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты для CommandsController.
 * <p>
 * Использует MockMvcBuilders.standaloneSetup() для тестирования MVC-слоя
 * без загрузки Spring-контекста. GlobalExceptionHandler подключается через setControllerAdvice().
 * Проверяет: HTTP-методы, пути, коды ответов, структуру JSON, параметры запроса.
 */
@ExtendWith(MockitoExtension.class)
class CommandsControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private CommandsService commandsService;

    @Mock
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommandsController(commandsService, healthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/commands/queryAll
    // -------------------------------------------------------------------------

    @Test
    void queryAll_returns200WithJson() throws Exception {
        QueryStateResponseDTO dto = new QueryStateResponseDTO("Line", Map.of());
        when(commandsService.queryAll()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/commands/queryAll")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void queryAll_withUnits_returnsUnitData() throws Exception {
        UnitPropertiesDTO props = new UnitPropertiesDTO(
                25, "25", "0", "", "0", "0", "", "item", "0",
                "0", "", "", "10", "5", "Level1Printers",
                "P1,P2", "P3", "Level1Cams", "C1,C2", "", "", "D1,D2", "0"
        );
        UnitStateDTO unitState = new UnitStateDTO("Run", "Print", 42, props);
        QueryStateResponseDTO dto = new QueryStateResponseDTO("Line", Map.of("u1", unitState));
        when(commandsService.queryAll()).thenReturn(dto);

        mockMvc.perform(get("/api/v1/commands/queryAll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Units.u1.Properties.command").value(25));
    }

    @Test
    void queryAll_callsCommandsServiceOnce() throws Exception {
        when(commandsService.queryAll()).thenReturn(new QueryStateResponseDTO("Line", Map.of()));

        mockMvc.perform(get("/api/v1/commands/queryAll"));

        verify(commandsService, times(1)).queryAll();
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/commands/setUnitVars
    // -------------------------------------------------------------------------

    @Test
    void setUnitVars_validParams_returns200() throws Exception {
        ChangeCommandResponseDTO dto = new ChangeCommandResponseDTO("Line", "SetUnitVars", Map.of());
        when(commandsService.setUnitVars(1, 128)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "1")
                        .param("value", "128"))
                .andExpect(status().isOk());

        verify(commandsService, times(1)).setUnitVars(1, 128);
    }

    @Test
    void setUnitVars_returnsAckResponse_with200() throws Exception {
        // Проверяем, что setUnitVars корректно делегирует в service и возвращает 200 OK.
        // Детальная проверка структуры ответа — в ApiMapperTest.
        ChangeCommandResponseDTO dto = new ChangeCommandResponseDTO("Line", "SetUnitVars",
                Map.of("u2", new UnitStateDTO(null, null, null,
                        new UnitPropertiesDTO(64, null, null, null, null, null, null, null,
                                null, null, null, null, null, null, null, null,
                                null, null, null, null, null, null, null))));
        when(commandsService.setUnitVars(2, 64)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "2")
                        .param("value", "64"))
                .andExpect(status().isOk());

        verify(commandsService, times(1)).setUnitVars(2, 64);
    }

    @Test
    void setUnitVars_missingUnitParam_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("value", "128"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commandsService);
    }

    @Test
    void setUnitVars_missingValueParam_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commandsService);
    }

    @Test
    void setUnitVars_unitParamIsString_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "notANumber")
                        .param("value", "128"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/commands/health/live
    // -------------------------------------------------------------------------

    @Test
    void live_returns200WithStatusUp() throws Exception {
        when(healthService.isAlive()).thenReturn(true);

        mockMvc.perform(get("/api/v1/commands/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/commands/health/ready
    // -------------------------------------------------------------------------

    @Test
    void ready_whenReady_returns200() throws Exception {
        when(healthService.isReady()).thenReturn(true);

        mockMvc.perform(get("/api/v1/commands/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void ready_whenNotReady_returns503() throws Exception {
        when(healthService.isReady()).thenReturn(false);

        mockMvc.perform(get("/api/v1/commands/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.ready").value(false));
    }

    // -------------------------------------------------------------------------
    // Неизвестный маршрут → не 2xx
    // -------------------------------------------------------------------------

    @Test
    void unknownRoute_returnsNon2xx() throws Exception {
        // standaloneSetup обрабатывает неизвестные маршруты через fallback GlobalExceptionHandler → 500
        mockMvc.perform(get("/api/v1/commands/doesNotExist"))
                .andExpect(status().is5xxServerError());
    }
}
