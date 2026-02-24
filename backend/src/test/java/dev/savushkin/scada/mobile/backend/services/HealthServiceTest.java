package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ScadaApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для HealthService.
 * Проверяет делегирование isAlive/isReady к ScadaApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private ScadaApplicationService applicationService;

    @InjectMocks
    private HealthService healthService;

    @Test
    void isAlive_delegatesToApplicationService_returnsTrue() {
        when(applicationService.isAlive()).thenReturn(true);

        assertTrue(healthService.isAlive());
        verify(applicationService, times(1)).isAlive();
    }

    @Test
    void isAlive_delegatesToApplicationService_returnsFalse() {
        when(applicationService.isAlive()).thenReturn(false);

        assertFalse(healthService.isAlive());
    }

    @Test
    void isReady_delegatesToApplicationService_returnsTrue() {
        when(applicationService.isReady()).thenReturn(true);

        assertTrue(healthService.isReady());
        verify(applicationService, times(1)).isReady();
    }

    @Test
    void isReady_delegatesToApplicationService_returnsFalse() {
        when(applicationService.isReady()).thenReturn(false);

        assertFalse(healthService.isReady());
    }
}
