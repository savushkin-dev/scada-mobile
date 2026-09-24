package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
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

class DeviceCounterResolverTest {

    private static final String INSTANCE_ID = "hassia5";

    private final PrintSrvTopologyRepository topologyRepo = mock(PrintSrvTopologyRepository.class);
    private final InstanceSnapshotRepository snapshotRepo = mock(InstanceSnapshotRepository.class);
    private final DeviceScadaRegistry registry = mock(DeviceScadaRegistry.class);
    private final DeviceCompositionService compositionService = mock(DeviceCompositionService.class);

    private DeviceCounterResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DeviceCounterResolver(topologyRepo, snapshotRepo, registry, compositionService);
        PrintSrvInstance inst = new PrintSrvInstance(
                INSTANCE_ID, "Hassia 5", 1L, "", 0,
                List.of("scada"), List.of(), List.of(), List.of(), List.of(),
                Map.of(), Map.of());
        when(topologyRepo.findByInstanceId(INSTANCE_ID)).thenReturn(Optional.of(inst));
        when(snapshotRepo.get(eq(INSTANCE_ID), eq("scada"))).thenReturn(snapshot("scada", Map.of()));
    }

    @Test
    void picksFirstShowCountersCameraByDisplayOrder() {
        givenLayout(
                entry("CamAgregation1", "aggregation_cam", 5, true),
                entry("CamAgregationBox1", "aggregation_box_cam", 0, true));
        givenSnapshot("CamAgregation1", Map.of("Succeeded", "100"));
        givenSnapshot("CamAgregationBox1", Map.of("Succeeded", "50", "Failed", "2"));

        DeviceCounterResolver.UnitCounters counters = resolver.resolveUnitCounters(INSTANCE_ID);

        // Box-камера раньше по display_order, хотя у aggregation-камеры счётчик больше
        assertThat(counters.read()).isEqualTo("50");
        assertThat(counters.unread()).isEqualTo("2");
    }

    @Test
    void fallsBackToAggregationCamWhenNoDevicesConfigured() {
        givenLayout();
        when(compositionService.getComposition(INSTANCE_ID))
                .thenReturn(new DeviceComposition(List.of(), List.of("CamAgregation"), List.of(), List.of()));
        givenSnapshot("CamAgregation", Map.of("Succeeded", "70", "Failed", "3"));

        DeviceCounterResolver.UnitCounters counters = resolver.resolveUnitCounters(INSTANCE_ID);

        assertThat(counters.read()).isEqualTo("70");
        assertThat(counters.unread()).isEqualTo("3");
    }

    @Test
    void explicitCounterDisableIsHonored() {
        // У аппарата есть записи устройств, но ни одного show_counters=true —
        // это намеренный запрет, fallback на камеры агрегации не применяется.
        givenLayout(entry("CamAgregation", "aggregation_cam", 0, false));
        when(compositionService.getComposition(INSTANCE_ID))
                .thenReturn(new DeviceComposition(List.of(), List.of("CamAgregation"), List.of(), List.of()));
        givenSnapshot("CamAgregation", Map.of("Succeeded", "70", "Failed", "3"));

        DeviceCounterResolver.UnitCounters counters = resolver.resolveUnitCounters(INSTANCE_ID);

        assertThat(counters.read()).isEqualTo("0");
        assertThat(counters.unread()).isEqualTo("0");
    }

    @Test
    void eanCheckerWithoutFlagNeverParticipates() {
        givenLayout(entry("CamEanChecker1", "checker_cam", 0, false));
        when(compositionService.getComposition(INSTANCE_ID)).thenReturn(DeviceComposition.empty());
        givenSnapshot("CamEanChecker1", Map.of("Succeeded", "999"));

        DeviceCounterResolver.UnitCounters counters = resolver.resolveUnitCounters(INSTANCE_ID);

        assertThat(counters.read()).isEqualTo("0");
        assertThat(counters.unread()).isEqualTo("0");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void givenLayout(DeviceEntry... entries) {
        when(registry.loadLayout(INSTANCE_ID))
                .thenReturn(new DeviceLayout(INSTANCE_ID, "Hassia 5", List.of(entries)));
    }

    private void givenSnapshot(String device, Map<String, String> raw) {
        when(snapshotRepo.get(INSTANCE_ID, device)).thenReturn(snapshot(device, raw));
    }

    private static DeviceSnapshot snapshot(String device, Map<String, String> raw) {
        UnitProperties properties = UnitProperties.builder().rawProperties(raw).build();
        return new DeviceSnapshot(device, Map.of("u1", new UnitSnapshot(1, "", "", null, properties)));
    }

    private static DeviceEntry entry(String code, String typeCode, int order, boolean showCounters) {
        return new DeviceEntry(code, code, null, null, order, showCounters, null, typeCode);
    }
}
