package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.DeviceGroupDTO;
import dev.savushkin.scada.mobile.backend.api.dto.DeviceMetaDTO;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceEntry;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceGroupServiceTest {

    private final DeviceGroupService groupService = new DeviceGroupService();

    @Test
    void buildsMachineGroupFirstThenStreamsByPrefix() {
        DeviceLayout layout = new DeviceLayout("grunwald11", "Grunwald 11", List.of(
                entry("Printer11", "printer", 0, null, false, null),
                entry("Printer12", "printer", 1, null, false, null),
                entry("CamAgregation1", "aggregation_cam", 2, "Dev041", true, null),
                entry("CamAgregationBox1", "aggregation_box_cam", 3, "Dev042", false, null),
                entry("CamAgregation2", "aggregation_cam", 4, "Dev043", true, null),
                entry("CamAgregationBox2", "aggregation_box_cam", 5, "Dev044", false, null),
                entry("CamEanChecker1", "checker_cam", 6, "Dev071", false, null),
                entry("CamEanChecker2", "checker_cam", 7, "Dev072", false, null)));
        Map<String, String> prefixes = Map.of(
                "CamAgregation1", "Dev041", "CamAgregationBox1", "Dev042",
                "CamAgregation2", "Dev043", "CamAgregationBox2", "Dev044",
                "CamEanChecker1", "Dev071", "CamEanChecker2", "Dev072");

        List<DeviceGroupDTO> groups = groupService.buildGroups(layout, prefixes);

        assertThat(groups).extracting(DeviceGroupDTO::label)
                .containsExactly("Grunwald 11", "Поток 1", "Поток 2");
        assertThat(groups).extracting(DeviceGroupDTO::order).containsExactly(0, 1, 2);
        // Машинная группа отсортирована по имени: CamEanChecker1/2 < Printer11/12
        assertThat(groups.get(0).codes()).containsExactly(
                "CamEanChecker1", "CamEanChecker2", "Printer11", "Printer12");
        assertThat(groups.get(1).codes()).containsExactly("CamAgregation1", "CamAgregationBox1");
        assertThat(groups.get(2).codes()).containsExactly("CamAgregation2", "CamAgregationBox2");
    }

    @Test
    void singleStreamIsNamedJustPotok() {
        DeviceLayout layout = new DeviceLayout("hassia5", "Hassia 5", List.of(
                entry("CamAgregation", "aggregation_cam", 0, "Dev041", true, null),
                entry("CamAgregationBox", "aggregation_box_cam", 1, "Dev042", false, null)));
        Map<String, String> prefixes = Map.of("CamAgregation", "Dev041", "CamAgregationBox", "Dev042");

        List<DeviceGroupDTO> groups = groupService.buildGroups(layout, prefixes);

        assertThat(groups).extracting(DeviceGroupDTO::label).containsExactly("Hassia 5", "Поток");
    }

    @Test
    void manualGroupLabelOverridesDefaults() {
        DeviceLayout layout = new DeviceLayout("trepko11", "Trepko 11", List.of(
                entry("CamAgregation", "aggregation_cam", 0, "Dev041", true, "Агрегация"),
                entry("Printer2", "printer", 1, null, false, "Поток")));
        Map<String, String> prefixes = Map.of("CamAgregation", "Dev041");

        List<DeviceGroupDTO> groups = groupService.buildGroups(layout, prefixes);

        assertThat(groups).extracting(DeviceGroupDTO::label)
                .containsExactly("Trepko 11", "Агрегация", "Поток");
        // Принтер вручную перенесён в «Поток» (как Printer2 на Hassia4)
        assertThat(groups.get(2).codes()).containsExactly("Printer2");
    }

    @Test
    void plainCheckerGoesToPotokWithoutCounters() {
        DeviceLayout layout = new DeviceLayout("hassia4", "Hassia 4", List.of(
                entry("CamChecker", "checker_cam", 0, null, false, null),
                entry("CamChecker1", "checker_cam", 1, null, false, null)));

        List<DeviceGroupDTO> groups = groupService.buildGroups(layout, Map.of());

        // Машинная группа всегда первая, даже пустая
        assertThat(groups).extracting(DeviceGroupDTO::label)
                .containsExactly("Hassia 4", "Поток", "Поток 1");
        assertThat(groups.get(0).codes()).isEmpty();
    }

    @Test
    void deviceMetaUsesOverrideAndCountersFlag() {
        DeviceLayout layout = new DeviceLayout("hassia5", "Hassia 5", List.of(
                new DeviceEntry("CamAgregation", "CamAgregation", "Камера 41",
                        null, 0, true, "Dev041", "aggregation_cam", false),
                entry("CamAgregationBox", "aggregation_box_cam", 1, "Dev042", false, null)));

        Map<String, DeviceMetaDTO> meta = groupService.buildDeviceMeta(layout);

        assertThat(meta.get("CamAgregation").displayName()).isEqualTo("Камера 41");
        assertThat(meta.get("CamAgregation").showCounters()).isTrue();
        assertThat(meta.get("CamAgregationBox").displayName()).isEqualTo("CamAgregationBox");
        assertThat(meta.get("CamAgregationBox").showCounters()).isFalse();
    }

    @Test
    void hiddenDevicesAreExcludedFromGroupsAndMeta() {
        DeviceLayout layout = new DeviceLayout("hassia4", "Hassia 4", List.of(
                entry("CamAgregation", "checker_cam", 0, "Dev041", true, null),
                entry("CamChecker", "checker_cam", 1, null, false, null, true)));

        List<DeviceGroupDTO> groups = groupService.buildGroups(layout, Map.of("CamAgregation", "Dev041"));
        Map<String, DeviceMetaDTO> meta = groupService.buildDeviceMeta(layout);

        List<String> allCodes = groups.stream().flatMap(g -> g.codes().stream()).toList();
        assertThat(allCodes).containsExactly("CamAgregation");
        assertThat(meta).containsOnlyKeys("CamAgregation");
    }

    private static DeviceEntry entry(String code, String typeCode, int order,
                                     String scadaPrefix, boolean showCounters, String groupLabel) {
        return entry(code, typeCode, order, scadaPrefix, showCounters, groupLabel, false);
    }

    private static DeviceEntry entry(String code, String typeCode, int order,
                                     String scadaPrefix, boolean showCounters, String groupLabel,
                                     boolean hidden) {
        return new DeviceEntry(code, code, null, groupLabel, order, showCounters, scadaPrefix, typeCode, hidden);
    }
}
