package dev.savushkin.scada.mobile.backend.exception;

import dev.savushkin.scada.mobile.backend.api.controller.CommandsController;
import dev.savushkin.scada.mobile.backend.api.dto.ErrorResponseDTO;
import dev.savushkin.scada.mobile.backend.services.CommandsService;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты для GlobalExceptionHandler.
 * <p>
 * Комбинирует два подхода:
 * <ol>
 *   <li>Прямые unit-тесты методов обработчика — для исключений, которые нельзя
 *       поднять через mock-сервис (checked exceptions: SocketException, IOException)
 *       и для Spring-специфичных исключений (NoResourceFoundException).</li>
 *   <li>MockMvc standaloneSetup — для сценариев, которые можно воспроизвести
 *       через обычный HTTP-запрос к контроллеру.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockMvc mockMvc;

    @Mock
    private CommandsService commandsService;

    @Mock
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommandsController(commandsService, healthService))
                .setControllerAdvice(handler)
                .build();
    }

    // -------------------------------------------------------------------------
    // SocketException → 503  (unit-test: прямой вызов обработчика)
    // -------------------------------------------------------------------------

    @Test
    void socketException_handler_returns503() {
        WebRequest request = buildWebRequest("/api/v1/commands/queryAll");
        SocketException ex = new SocketException("Connection refused");

        ResponseEntity<ErrorResponseDTO> response = handler.handleSocketException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().status());
        assertTrue(response.getBody().message().contains("PrintSrv недоступен"));
        assertEquals("/api/v1/commands/queryAll", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
    }

    // -------------------------------------------------------------------------
    // IOException → 500  (unit-test: прямой вызов обработчика)
    // -------------------------------------------------------------------------

    @Test
    void ioException_handler_returns500() {
        WebRequest request = buildWebRequest("/api/v1/commands/queryAll");
        IOException ex = new IOException("JSON parse error");

        ResponseEntity<ErrorResponseDTO> response = handler.handleIOException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertTrue(response.getBody().message().contains("Ошибка обработки данных"));
    }

    // -------------------------------------------------------------------------
    // NoResourceFoundException → 404  (unit-test: прямой вызов обработчика)
    // -------------------------------------------------------------------------

    @Test
    void noResourceFound_handler_returns404() {
        WebRequest request = buildWebRequest("/api/v1/commands/doesNotExist");
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/api/v1/commands/doesNotExist", "Not found");

        ResponseEntity<ErrorResponseDTO> response = handler.handleNoResourceFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Ресурс не найден", response.getBody().message());
    }

    // -------------------------------------------------------------------------
    // IllegalStateException → 503  (via MockMvc)
    // -------------------------------------------------------------------------

    @Test
    void illegalStateException_returns503() throws Exception {
        when(commandsService.queryAll()).thenThrow(new IllegalStateException("Snapshot not available yet"));

        mockMvc.perform(get("/api/v1/commands/queryAll"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Snapshot not available yet"));
    }

    // -------------------------------------------------------------------------
    // IllegalArgumentException → 400  (via MockMvc)
    // -------------------------------------------------------------------------

    @Test
    void illegalArgumentException_returns400() throws Exception {
        when(commandsService.setUnitVars(1, 1)).thenThrow(new IllegalArgumentException("bad param"));

        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "1")
                        .param("value", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Некорректный запрос")));
    }

    // -------------------------------------------------------------------------
    // BufferOverflowException → 503  (via MockMvc)
    // -------------------------------------------------------------------------

    @Test
    void bufferOverflowException_returns503() throws Exception {
        when(commandsService.setUnitVars(1, 1))
                .thenThrow(new BufferOverflowException("Buffer full"));

        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "1")
                        .param("value", "1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Buffer full"));
    }

    // -------------------------------------------------------------------------
    // Missing parameter → 400  (via MockMvc)
    // -------------------------------------------------------------------------

    @Test
    void missingRequiredParameter_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // Type mismatch → 400  (via MockMvc)
    // -------------------------------------------------------------------------

    @Test
    void typeMismatchParameter_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/commands/setUnitVars")
                        .param("unit", "abc")
                        .param("value", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // -------------------------------------------------------------------------
    // Method Not Allowed → 405  (via MockMvc)
    // -------------------------------------------------------------------------

    @Test
    void wrongHttpMethod_returns405() throws Exception {
        mockMvc.perform(post("/api/v1/commands/queryAll"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    // -------------------------------------------------------------------------
    // Fallback Exception → 500  (via MockMvc)
    // -------------------------------------------------------------------------

    @Test
    void unexpectedException_returns500() throws Exception {
        when(commandsService.queryAll()).thenThrow(new RuntimeException("unexpected failure"));

        mockMvc.perform(get("/api/v1/commands/queryAll"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервера"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static WebRequest buildWebRequest(String path) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRequestURI(path);
        return new ServletWebRequest(servletRequest);
    }
}

