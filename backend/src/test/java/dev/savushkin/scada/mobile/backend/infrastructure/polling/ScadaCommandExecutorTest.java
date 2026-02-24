package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.QueryAllCommand;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.SetUnitVars;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.SetUnitVarsRequestDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты для ScadaCommandExecutor.
 * Проверяет: queryAllSnapshot (маппинг DTO → domain), executeSetUnitVars (формирование запроса).
 */
@ExtendWith(MockitoExtension.class)
class ScadaCommandExecutorTest {

    @Mock
    private QueryAllCommand queryAllCommand;
    @Mock
    private SetUnitVars setUnitVarsCommand;
    @Mock
    private PrintSrvMapper printSrvMapper;

    @InjectMocks
    private ScadaCommandExecutor executor;

    // -------------------------------------------------------------------------
    // queryAllSnapshot
    // -------------------------------------------------------------------------

    @Test
    void queryAllSnapshot_callsQueryAllCommand_andMapper() throws Exception {
        QueryAllResponseDTO dto = new QueryAllResponseDTO("Line", "QueryAll", Map.of());
        DeviceSnapshot domain = new DeviceSnapshot("Line", Map.of());

        when(queryAllCommand.execute(any())).thenReturn(dto);
        when(printSrvMapper.toDomainDeviceSnapshot(dto)).thenReturn(domain);

        DeviceSnapshot result = executor.queryAllSnapshot();

        assertSame(domain, result);
        verify(queryAllCommand, times(1)).execute(any());
        verify(printSrvMapper, times(1)).toDomainDeviceSnapshot(dto);
    }

    @Test
    void queryAllSnapshot_whenCommandThrows_propagatesException() throws Exception {
        when(queryAllCommand.execute(any())).thenThrow(new IOException("Connection refused"));

        assertThrows(Exception.class, () -> executor.queryAllSnapshot());
    }

    // -------------------------------------------------------------------------
    // executeSetUnitVars
    // -------------------------------------------------------------------------

    @Test
    void executeSetUnitVars_emptyMap_doesNotCallSetUnitVars() throws IOException {
        executor.executeSetUnitVars(Map.of());

        verifyNoInteractions(setUnitVarsCommand);
    }

    @Test
    void executeSetUnitVars_nullMap_doesNotCallSetUnitVars() throws IOException {
        executor.executeSetUnitVars(null);

        verifyNoInteractions(setUnitVarsCommand);
    }

    @Test
    void executeSetUnitVars_singleCommand_callsSetUnitVarsOnce() throws Exception {
        WriteCommand cmd = new WriteCommand(100L, 1, 128);
        when(setUnitVarsCommand.execute(any())).thenReturn(null);

        executor.executeSetUnitVars(Map.of(1, cmd));

        verify(setUnitVarsCommand, times(1)).execute(any());
    }

    @Test
    void executeSetUnitVars_singleCommand_buildsCorrectRequest() throws Exception {
        WriteCommand cmd = new WriteCommand(100L, 2, 64);
        ArgumentCaptor<SetUnitVarsRequestDTO> captor = ArgumentCaptor.forClass(SetUnitVarsRequestDTO.class);
        when(setUnitVarsCommand.execute(captor.capture())).thenReturn(null);

        executor.executeSetUnitVars(Map.of(2, cmd));

        SetUnitVarsRequestDTO req = captor.getValue();
        assertEquals("Line", req.deviceName());
        assertEquals(2, req.unit());
        assertEquals("SetUnitVars", req.command());
        assertNotNull(req.parameters());
        assertEquals(64, req.parameters().command());
    }

    @Test
    void executeSetUnitVars_multipleCommands_callsSetUnitVarsForEach() throws Exception {
        WriteCommand cmd1 = new WriteCommand(100L, 1, 10);
        WriteCommand cmd2 = new WriteCommand(100L, 2, 20);
        when(setUnitVarsCommand.execute(any())).thenReturn(null);

        executor.executeSetUnitVars(Map.of(1, cmd1, 2, cmd2));

        verify(setUnitVarsCommand, times(2)).execute(any());
    }

    @Test
    void executeSetUnitVars_whenSetUnitVarsThrows_propagatesIOException() throws Exception {
        WriteCommand cmd = new WriteCommand(100L, 1, 128);
        doThrow(new IOException("write failed")).when(setUnitVarsCommand).execute(any());

        assertThrows(IOException.class,
                () -> executor.executeSetUnitVars(Map.of(1, cmd)));
    }
}
