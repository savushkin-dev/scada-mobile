package dev.savushkin.scada.mobile.backend.exception;

import dev.savushkin.scada.mobile.backend.api.controller.Controller;
import dev.savushkin.scada.mobile.backend.api.dto.ErrorResponseDTO;
import dev.savushkin.scada.mobile.backend.services.HealthService;
import dev.savushkin.scada.mobile.backend.services.WorkshopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты для GlobalExceptionHandler.
 */
@WebMvcTest(Controller.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkshopService workshopService;

    @MockBean
    private HealthService healthService;

    @Value("${scada.api.base-path}")
    private String apiBasePath;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // -------------------------------------------------------------------------
    // SocketException → 503  (unit-test)
    // -------------------------------------------------------------------------

    @Test
    void socketException_handler_returns503() {
        WebRequest request = buildWebRequest(apiBasePath + "/workshops");
        SocketException ex = new SocketException("Connection refused");

        ResponseEntity<ErrorResponseDTO> response = handler.handleSocketException(ex, request);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(503, response.getBody().status());
        assertTrue(response.getBody().message().contains("PrintSrv недоступен"));
    }

    // -------------------------------------------------------------------------
    // IOException → 500  (unit-test)
    // -------------------------------------------------------------------------

    @Test
    void ioException_handler_returns500() {
        WebRequest request = buildWebRequest(apiBasePath + "/workshops");
        IOException ex = new IOException("JSON parse error");

        ResponseEntity<ErrorResponseDTO> response = handler.handleIOException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertTrue(response.getBody().message().contains("Ошибка обработки данных"));
    }

    // -------------------------------------------------------------------------
    // NoResourceFoundException → 404  (unit-test)
    // -------------------------------------------------------------------------

    @Test
    void noResourceFound_handler_returns404() {
        WebRequest request = buildWebRequest(apiBasePath + "/doesNotExist");
        NoResourceFoundException ex = new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, apiBasePath + "/doesNotExist", "Not found");

        ResponseEntity<ErrorResponseDTO> response = handler.handleNoResourceFoundException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Ресурс не найден", response.getBody().message());
    }

    // -------------------------------------------------------------------------
    // IllegalStateException → 503 via MockMvc
    // -------------------------------------------------------------------------

    @Test
    void illegalStateException_returns503() throws Exception {
        when(healthService.isReady()).thenThrow(new IllegalStateException("Service not ready"));

        mockMvc.perform(get(apiBasePath + "/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // -------------------------------------------------------------------------
    // Fallback Exception → 500 via MockMvc
    // -------------------------------------------------------------------------

    @Test
    void unexpectedException_returns500() throws Exception {
        when(healthService.isAlive()).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get(apiBasePath + "/health/live"))
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
