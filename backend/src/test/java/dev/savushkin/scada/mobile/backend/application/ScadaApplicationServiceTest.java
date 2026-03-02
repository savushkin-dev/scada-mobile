package dev.savushkin.scada.mobile.backend.application;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScadaApplicationServiceTest {

    @Mock
    private InstanceSnapshotRepository snapshotRepository;

    @InjectMocks
    private ScadaApplicationService service;

    @Test
    void isReady_whenSnapshotsExist_returnsTrue() {
        when(snapshotRepository.hasAnySnapshot()).thenReturn(true);
        assertTrue(service.isReady());
    }

    @Test
    void isReady_whenNoSnapshots_returnsFalse() {
        when(snapshotRepository.hasAnySnapshot()).thenReturn(false);
        assertFalse(service.isReady());
    }

    @Test
    void isAlive_alwaysReturnsTrue() {
        assertTrue(service.isAlive());
        verifyNoInteractions(snapshotRepository);
    }
}
