package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceScadaRegistryTest {

    private static final String INSTANCE_ID = "grunwald11";

    private final DeviceJpaRepository deviceRepository = mock(DeviceJpaRepository.class);
    private final UnitJpaRepository unitRepository = mock(UnitJpaRepository.class);
    private final DeviceCompositionService compositionService = mock(DeviceCompositionService.class);

    private DeviceScadaRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DeviceScadaRegistry(deviceRepository, unitRepository, compositionService);
        UnitEntity unit = new UnitEntity();
        unit.setName("Grunwald 11");
        when(unitRepository.findByPrintsrvInstanceId(INSTANCE_ID)).thenReturn(Optional.of(unit));
        when(compositionService.getRuntimeComposition(INSTANCE_ID)).thenReturn(null);
    }

    @Test
    void configuredPrefixWinsOverIndexDefault() {
        givenDevices(device("CamAgregation1", "aggregation_cam", "Dev099"));
        givenComposition(List.of(), List.of("CamAgregation1"), List.of(), List.of());

        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "CamAgregation1")).isEqualTo("Dev099");
    }

    @Test
    void resolvesDefaultPrefixesByCompositionIndex() {
        givenDevices(
                device("Printer11", "printer", null),
                device("CamAgregation1", "aggregation_cam", null),
                device("CamAgregation2", "aggregation_cam", null),
                device("CamAgregationBox1", "aggregation_box_cam", null),
                device("CamEanChecker1", "checker_cam", null),
                device("CamChecker", "checker_cam", null));
        givenComposition(
                List.of("Printer11"),
                List.of("CamAgregation1", "CamAgregation2"),
                List.of("CamAgregationBox1"),
                List.of("CamEanChecker1", "CamChecker"));

        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "Printer11")).isEqualTo("LineDev011");
        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "CamAgregation1")).isEqualTo("Dev041");
        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "CamAgregation2")).isEqualTo("Dev043");
        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "CamAgregationBox1")).isEqualTo("Dev042");
        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "CamEanChecker1")).isEqualTo("Dev071");
        // Обычный CamChecker scada-префикса не имеет
        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "CamChecker")).isNull();
    }

    @Test
    void printerPrefixSupportsBothZeroPaddingVariants() {
        givenDevices(device("Printer2", "printer", null));
        givenComposition(List.of("Printer2"), List.of(), List.of(), List.of());

        assertThat(registry.resolveScadaPrefixes(INSTANCE_ID, "Printer2"))
                .containsExactly("LineDev02", "LineDev002");
        // Префикс LineDev02 (Hassia4) резолвится в то же устройство
        assertThat(registry.findByScadaPrefix(INSTANCE_ID, "LineDev02"))
                .map(DeviceScadaRegistry.DeviceEntry::code)
                .contains("Printer2");
    }

    @Test
    void misclassifiedAggregationCamResolvesByName() {
        // Реальный случай из seed-данных: CamAgregation заведён как checker_cam.
        // Name-based правило важнее типа каталога и индекса.
        givenDevices(device("CamAgregation", "checker_cam", null));
        givenComposition(List.of(), List.of("CamAgregation"), List.of(), List.of());

        assertThat(registry.resolveScadaPrefix(INSTANCE_ID, "CamAgregation")).isEqualTo("Dev041");
        assertThat(registry.findByScadaPrefix(INSTANCE_ID, "Dev041"))
                .map(DeviceScadaRegistry.DeviceEntry::code)
                .contains("CamAgregation");
    }

    @Test
    void defaultShowCountersOnlyForAggregationCams() {
        assertThat(ScadaKeyMapper.defaultShowCounters("checker_cam", "CamAgregation")).isTrue();
        assertThat(ScadaKeyMapper.defaultShowCounters("aggregation_cam", "CamAgregation2")).isTrue();
        assertThat(ScadaKeyMapper.defaultShowCounters("aggregation_box_cam", "CamAgregationBox")).isFalse();
        assertThat(ScadaKeyMapper.defaultShowCounters("checker_cam", "CamChecker")).isFalse();
        assertThat(ScadaKeyMapper.defaultShowCounters("checker_cam", "CamEanChecker1")).isFalse();
        assertThat(ScadaKeyMapper.defaultShowCounters("printer", "Printer11")).isFalse();
    }

    @Test
    void findsDeviceByDefaultPrefix() {
        givenDevices(device("CamAgregation1", "aggregation_cam", null));
        givenComposition(List.of(), List.of("CamAgregation1"), List.of(), List.of());

        assertThat(registry.findByScadaPrefix(INSTANCE_ID, "Dev041"))
                .map(DeviceScadaRegistry.DeviceEntry::code)
                .contains("CamAgregation1");
        assertThat(registry.findByScadaPrefix(INSTANCE_ID, "Dev099")).isEmpty();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void givenDevices(DeviceEntity... devices) {
        when(deviceRepository.findByUnit_PrintsrvInstanceId(eq(INSTANCE_ID))).thenReturn(List.of(devices));
    }

    private void givenComposition(List<String> printers, List<String> aggr, List<String> box, List<String> checker) {
        when(compositionService.getComposition(INSTANCE_ID))
                .thenReturn(new DeviceComposition(printers, aggr, box, checker));
    }

    private static DeviceEntity device(String code, String typeCode, String scadaPrefix) {
        DeviceTypeEntity type = new DeviceTypeEntity();
        type.setCode(typeCode);
        DeviceCatalogEntity catalog = new DeviceCatalogEntity();
        catalog.setCode(code);
        catalog.setName(code);
        catalog.setType(type);
        catalog.setActive(true);
        DeviceEntity device = new DeviceEntity();
        device.setCatalog(catalog);
        device.setScadaPrefix(scadaPrefix);
        return device;
    }
}
