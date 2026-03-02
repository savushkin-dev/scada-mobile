package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanCycleSchedulerTest {

    @Mock
    private PrintSrvClientRegistry registry;
    @Mock
    private PrintSrvMapper mapper;
    @Mock
    private InstanceSnapshotRepository snapshotRepo;
    @Mock
    private PrintSrvClient client1;
    @Mock
    private PrintSrvClient client2;

    private ScanCycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(registry.getInstanceIds()).thenReturn(new LinkedHashSet<>(List.of("inst1", "inst2")));
        when(registry.get("inst1")).thenReturn(client1);
        when(registry.get("inst2")).thenReturn(client2);
        scheduler = new ScanCycleScheduler(registry, mapper, snapshotRepo);
    }

    @Test
    void scanCycle_queriesAllDevicesForAllInstances() throws IOException {
        QueryAllResponseDTO dto = mock(QueryAllResponseDTO.class);
        DeviceSnapshot snapshot = mock(DeviceSnapshot.class);

        when(client1.queryAll(anyString())).thenReturn(dto);
        when(client2.queryAll(anyString())).thenReturn(dto);
        when(mapper.toDomainDeviceSnapshot(dto)).thenReturn(snapshot);

        scheduler.scanCycle();

        // Each instance should be queried for each device
        for (String device : ScanCycleScheduler.DEVICES) {
            verify(client1).queryAll(device);
            verify(client2).queryAll(device);
            verify(snapshotRepo).save("inst1", device, snapshot);
            verify(snapshotRepo).save("inst2", device, snapshot);
        }
    }

    @Test
    void scanCycle_continuesWhenOneDeviceFails() throws IOException {
        QueryAllResponseDTO dto = mock(QueryAllResponseDTO.class);
        DeviceSnapshot snapshot = mock(DeviceSnapshot.class);

        // client1 fails for "Line" but succeeds for others
        when(client1.queryAll("Line")).thenThrow(new IOException("connection refused"));
        when(client1.queryAll(argThat(d -> !"Line".equals(d)))).thenReturn(dto);
        when(client2.queryAll(anyString())).thenReturn(dto);
        when(mapper.toDomainDeviceSnapshot(dto)).thenReturn(snapshot);

        scheduler.scanCycle();

        // Line for inst1 should not be saved, but other devices should
        verify(snapshotRepo, never()).save(eq("inst1"), eq("Line"), any());
        verify(snapshotRepo).save(eq("inst1"), eq("scada"), eq(snapshot));
        // inst2 should be fully queried
        verify(snapshotRepo).save(eq("inst2"), eq("Line"), eq(snapshot));
    }

    @Test
    void scanCycle_continuesWhenOneInstanceFails() throws IOException {
        QueryAllResponseDTO dto = mock(QueryAllResponseDTO.class);
        DeviceSnapshot snapshot = mock(DeviceSnapshot.class);

        // client1 fails for all
        when(client1.queryAll(anyString())).thenThrow(new IOException("offline"));
        // client2 succeeds for all
        when(client2.queryAll(anyString())).thenReturn(dto);
        when(mapper.toDomainDeviceSnapshot(dto)).thenReturn(snapshot);

        scheduler.scanCycle();

        // inst1 should have no saves
        verify(snapshotRepo, never()).save(eq("inst1"), anyString(), any());
        // inst2 should have all saves
        for (String device : ScanCycleScheduler.DEVICES) {
            verify(snapshotRepo).save("inst2", device, snapshot);
        }
    }
}
