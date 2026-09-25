package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceEntry;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceLayout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnitDetailServiceErrorsTest {

    private static final String INSTANCE_ID = "hassia5";

    private final PrintSrvTopologyRepository topologyRepo = mock(PrintSrvTopologyRepository.class);
    private final InstanceSnapshotRepository snapshotRepo = mock(InstanceSnapshotRepository.class);
    private final DeviceCompositionService compositionService = mock(DeviceCompositionService.class);
    private final DeviceScadaRegistry registry = mock(DeviceScadaRegistry.class);
    private final DeviceCounterResolver counterResolver = mock(DeviceCounterResolver.class);

    private UnitDetailService service;

    @BeforeEach
    void setUp() {
        service = new UnitDetailService(
                topologyRepo, snapshotRepo, new UnitErrorStore(), compositionService,
                registry, counterResolver, new DeviceGroupService());

        PrintSrvInstance inst = new PrintSrvInstance(
                INSTANCE_ID, "Hassia 5", 1L, "", 0,
                List.of("scada"), List.of(), List.of("CamAgregation"), List.of(), List.of(),
                Map.of(), Map.of());
        when(topologyRepo.findByInstanceId(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(compositionService.getComposition(INSTANCE_ID))
                .thenReturn(new DeviceComposition(List.of(), List.of("CamAgregation"), List.of(), List.of()));
        when(compositionService.getRuntimeComposition(INSTANCE_ID)).thenReturn(null);

        DeviceEntry entry = new DeviceEntry(
                "CamAgregation", "CamAgregation", "Камера 41", null, 0, true, null, "aggregation_cam", false);
        DeviceLayout layout = new DeviceLayout(INSTANCE_ID, "Hassia 5", List.of(entry));
        when(registry.loadLayout(INSTANCE_ID)).thenReturn(layout);
        when(registry.resolveScadaPrefixes(eq(INSTANCE_ID), anyString())).thenReturn(List.of("Dev041"));
        when(registry.resolveScadaPrefix(eq(INSTANCE_ID), anyString())).thenReturn("Dev041");
        when(registry.findByScadaPrefix(eq(layout), eq("Dev041"))).thenReturn(Optional.of(entry));
    }

    @Test
    void suppressesBareErrorWhenSpecificFlagIsActive() {
        givenScadaFlags(Map.of("Dev041Connection", "1", "Dev041Error", "1"));

        List<DeviceError> errors = service.extractActiveErrors(INSTANCE_ID);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().objectName()).isEqualTo("Поток. Камера 41");
        assertThat(errors.getFirst().propertyDesc()).isEqualTo("Dev041Connection");
        assertThat(errors.getFirst().description()).isEqualTo("Нет связи с устройством");
    }

    @Test
    void keepsBareErrorWhenItIsTheOnlyActiveFlag() {
        givenScadaFlags(Map.of("Dev041Error", "1"));

        List<DeviceError> errors = service.extractActiveErrors(INSTANCE_ID);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().propertyDesc()).isEqualTo("Dev041Error");
        assertThat(errors.getFirst().objectName()).isEqualTo("Поток. Камера 41");
    }

    @Test
    void keepsSpecificFlagsWithoutBareError() {
        givenScadaFlags(Map.of("Dev041Dublicate", "1", "Dev041Batch", "1", "Dev041Error", "0"));

        List<DeviceError> errors = service.extractActiveErrors(INSTANCE_ID);

        assertThat(errors).extracting(DeviceError::propertyDesc)
                .containsExactlyInAnyOrder("Dev041Dublicate", "Dev041Batch");
    }

    @Test
    void ignoresErrorsOfUnknownDevices() {
        givenScadaFlags(Map.of("Dev099Connection", "1"));

        assertThat(service.extractActiveErrors(INSTANCE_ID)).isEmpty();
    }

    private void givenScadaFlags(Map<String, String> flags) {
        UnitProperties properties = UnitProperties.builder().rawProperties(flags).build();
        DeviceSnapshot scada = new DeviceSnapshot(
                "scada", Map.of("u1", new UnitSnapshot(1, "", "", null, properties)));
        when(snapshotRepo.get(INSTANCE_ID, "scada")).thenReturn(scada);
    }
}
