package dev.savushkin.scada.mobile.backend.application;

import dev.savushkin.scada.mobile.backend.application.ports.DeviceSnapshotReader;
import dev.savushkin.scada.mobile.backend.application.ports.PendingWriteCommandsPort;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import dev.savushkin.scada.mobile.backend.exception.BufferOverflowException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScadaApplicationServiceTest {

    @Mock
    private DeviceSnapshotReader snapshotReader;
    @Mock
    private PendingWriteCommandsPort commandBuffer;

    @InjectMocks
    private ScadaApplicationService scadaApplicationService;

    @Test
    void getCurrentState_DeviceSnapshotIsNOTNull() {
        DeviceSnapshot mockSnapshot = new DeviceSnapshot("TestDevice", Map.of());
        when(snapshotReader.getLatestOrNull()).thenReturn(mockSnapshot);

        DeviceSnapshot deviceSnapshot = scadaApplicationService.getCurrentState();

        assertSame(mockSnapshot, deviceSnapshot);
        verifyNoInteractions(commandBuffer);
        verify(snapshotReader, times(1)).getLatestOrNull();
    }

    @Test
    void getCurrentState_DeviceSnapshotIsNull() {
        when(snapshotReader.getLatestOrNull()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> scadaApplicationService.getCurrentState());
        verifyNoInteractions(commandBuffer);
        verify(snapshotReader, times(1)).getLatestOrNull();
    }

    @Test
    void submitWriteCommand_EnqueuesCommandWithCorrectFields() {
        int unitNumber = 1;
        int value = 128;

        scadaApplicationService.submitWriteCommand(unitNumber, value);

        ArgumentCaptor<WriteCommand> captor = ArgumentCaptor.forClass(WriteCommand.class);
        verify(commandBuffer, times(1)).enqueue(captor.capture());
        verifyNoInteractions(snapshotReader);

        WriteCommand captured = captor.getValue();
        assertEquals(unitNumber, captured.unitNumber());
        assertEquals(value, captured.commandValue());
    }

    @Test
    void submitWriteCommand_WhenBufferOverflows_ThrowsBufferOverflowException() {
        doThrow(new BufferOverflowException("Buffer is full"))
                .when(commandBuffer).enqueue(any(WriteCommand.class));

        assertThrows(BufferOverflowException.class,
                () -> scadaApplicationService.submitWriteCommand(1, 128));
    }

    @Test
    void isReady_WhenSnapshotExists_ReturnsTrue() {
        when(snapshotReader.getLatestOrNull()).thenReturn(new DeviceSnapshot("TestDevice", Map.of()));

        assertTrue(scadaApplicationService.isReady());
    }

    @Test
    void isReady_WhenSnapshotIsNull_ReturnsFalse() {
        when(snapshotReader.getLatestOrNull()).thenReturn(null);

        assertFalse(scadaApplicationService.isReady());
    }

    @Test
    void isAlive_AlwaysReturnsTrue() {
        assertTrue(scadaApplicationService.isAlive());
        verifyNoInteractions(snapshotReader, commandBuffer);
    }
}
