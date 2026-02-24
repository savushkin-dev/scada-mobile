package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.ApiMapper;
import dev.savushkin.scada.mobile.backend.api.dto.ChangeCommandResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueryStateResponseDTO;
import dev.savushkin.scada.mobile.backend.application.ScadaApplicationService;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.exception.BufferOverflowException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для CommandsService.
 * Проверяет делегирование к ScadaApplicationService и ApiMapper.
 */
@ExtendWith(MockitoExtension.class)
class CommandsServiceTest {

    @Mock
    private ScadaApplicationService applicationService;
    @Mock
    private ApiMapper apiMapper;

    @InjectMocks
    private CommandsService commandsService;

    // -------------------------------------------------------------------------
    // queryAll
    // -------------------------------------------------------------------------

    @Test
    void queryAll_getsSnapshotAndMapsToDto() {
        DeviceSnapshot snapshot = new DeviceSnapshot("Line", Map.of());
        QueryStateResponseDTO dto = new QueryStateResponseDTO("Line", Map.of());

        when(applicationService.getCurrentState()).thenReturn(snapshot);
        when(apiMapper.toApiQueryStateResponse(snapshot)).thenReturn(dto);

        QueryStateResponseDTO result = commandsService.queryAll();

        assertSame(dto, result);
        verify(applicationService, times(1)).getCurrentState();
        verify(apiMapper, times(1)).toApiQueryStateResponse(snapshot);
    }

    @Test
    void queryAll_whenNoSnapshot_propagatesIllegalState() {
        when(applicationService.getCurrentState()).thenThrow(new IllegalStateException("No snapshot"));

        assertThrows(IllegalStateException.class, () -> commandsService.queryAll());
        verifyNoInteractions(apiMapper);
    }

    // -------------------------------------------------------------------------
    // setUnitVars
    // -------------------------------------------------------------------------

    @Test
    void setUnitVars_submitCommandAndReturnsAck() {
        ChangeCommandResponseDTO dto = new ChangeCommandResponseDTO("Line", "SetUnitVars", Map.of());
        when(apiMapper.toApiChangeCommandResponse(1, 128)).thenReturn(dto);

        ChangeCommandResponseDTO result = commandsService.setUnitVars(1, 128);

        assertSame(dto, result);
        verify(applicationService, times(1)).submitWriteCommand(1, 128);
        verify(apiMapper, times(1)).toApiChangeCommandResponse(1, 128);
    }

    @Test
    void setUnitVars_whenBufferOverflow_propagatesException() {
        doThrow(new BufferOverflowException("Buffer full"))
                .when(applicationService).submitWriteCommand(anyInt(), anyInt());

        assertThrows(BufferOverflowException.class,
                () -> commandsService.setUnitVars(1, 128));
        verifyNoInteractions(apiMapper);
    }
}
