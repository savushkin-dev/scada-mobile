package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.DeviceGroupDTO;
import dev.savushkin.scada.mobile.backend.api.dto.DeviceMetaDTO;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceEntry;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сборка групп устройств аппарата для топологии фронта (паритет со старой SCADA).
 *
 * <p>Дефолты (PRINTSRV_UI_MAP.md §3):
 * <ul>
 *   <li>принтеры и EAN-камеры ({@code CamEanChecker*}) → группа машины (имя автомата);</li>
 *   <li>{@code CamAgregation[Box][N]} → «Поток k», где k выводится из scada-префикса
 *       ({@code Dev04(2k−1)}/{@code Dev04(2k)}); если поток один — просто «Поток»;</li>
 *   <li>{@code CamChecker[N]} → «Поток N» без счётчиков;</li>
 *   <li>ручной {@code group_label} переопределяет любое правило умолчания.</li>
 * </ul>
 *
 * <p>Группа машины всегда первая; остальные группы — по минимальному
 * {@code display_order} участников, при равенстве — по метке.
 */
@Service
public class DeviceGroupService {

    private static final Pattern DEV_PREFIX_NUMBER = Pattern.compile("Dev(\\d+)");
    private static final Pattern CAM_CHECKER_NUMBER = Pattern.compile("CamChecker(\\d*)");

    private static final String TYPE_PRINTER = "printer";
    private static final String TYPE_AGGREGATION_CAM = "aggregation_cam";
    private static final String TYPE_AGGREGATION_BOX_CAM = "aggregation_box_cam";

    /**
     * Разрешает метку группы устройства: ручной {@code group_label} важнее правил умолчания.
     *
     * @param layout          раскладка аппарата
     * @param entry           устройство
     * @param scadaPrefixByCode scada-префиксы устройств (для вычисления номера потока)
     */
    public @NonNull String resolveGroupLabel(
            @NonNull DeviceLayout layout,
            @NonNull DeviceEntry entry,
            @NonNull Map<String, String> scadaPrefixByCode
    ) {
        if (entry.groupLabel() != null && !entry.groupLabel().isBlank()) {
            return entry.groupLabel().trim();
        }
        String code = entry.code();
        // Классификация по runtime-коду важнее типа каталога: в реальных данных
        // встречаются misclassified записи (CamAgregation как checker_cam).
        if (code.startsWith("CamAgregation")) {
            return aggregationGroupLabel(layout, entry, scadaPrefixByCode);
        }
        if (code.startsWith("CamChecker")) {
            return checkerGroupLabel(entry);
        }
        return switch (entry.typeCode()) {
            case TYPE_AGGREGATION_CAM, TYPE_AGGREGATION_BOX_CAM ->
                    aggregationGroupLabel(layout, entry, scadaPrefixByCode);
            case null -> layout.unitDisplayName();
            default -> layout.unitDisplayName();
        };
    }

    /**
     * Строит упорядоченный список групп топологии.
     */
    public @NonNull List<DeviceGroupDTO> buildGroups(
            @NonNull DeviceLayout layout,
            @NonNull Map<String, String> scadaPrefixByCode
    ) {
        Map<String, List<DeviceEntry>> byLabel = new LinkedHashMap<>();
        List<DeviceEntry> sorted = new ArrayList<>(layout.entries());
        sorted.sort(Comparator.comparingInt(DeviceEntry::displayOrder).thenComparing(DeviceEntry::code));
        for (DeviceEntry entry : sorted) {
            if (entry.hidden()) {
                continue; // скрытые устройства на экран не выводятся
            }
            byLabel.computeIfAbsent(resolveGroupLabel(layout, entry, scadaPrefixByCode),
                    k -> new ArrayList<>()).add(entry);
        }

        String machineLabel = layout.unitDisplayName();
        // Группа машины всегда первая, даже если пуста (стабильный порядок на экране)
        byLabel.computeIfAbsent(machineLabel, k -> new ArrayList<>());
        // Группы после машинной — по минимальному display_order участника, затем по метке
        Map<String, List<DeviceEntry>> tail = new TreeMap<>(
                Comparator.<String>comparingInt(label -> byLabel.get(label).getFirst().displayOrder())
                        .thenComparing(label -> label));
        List<Map.Entry<String, List<DeviceEntry>>> ordered = new ArrayList<>();
        byLabel.forEach((label, entries) -> {
            if (label.equals(machineLabel)) {
                ordered.addFirst(Map.entry(label, entries));
            } else {
                tail.put(label, entries);
            }
        });
        ordered.addAll(tail.entrySet());

        List<DeviceGroupDTO> groups = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<String, List<DeviceEntry>> group = ordered.get(i);
            List<String> codes = group.getValue().stream().map(DeviceEntry::code).toList();
            groups.add(new DeviceGroupDTO(group.getKey(), i, codes));
        }
        return List.copyOf(groups);
    }

    /**
     * Мета-информация устройств для фронта: отображаемое имя (с per-unit override)
     * и признак счётчиков.
     */
    public @NonNull Map<String, DeviceMetaDTO> buildDeviceMeta(@NonNull DeviceLayout layout) {
        Map<String, DeviceMetaDTO> meta = new LinkedHashMap<>();
        for (DeviceEntry entry : layout.entries()) {
            if (entry.hidden()) {
                continue;
            }
            meta.put(entry.code(), new DeviceMetaDTO(entry.effectiveDisplayName(), entry.showCounters()));
        }
        return Map.copyOf(meta);
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    /**
     * «Поток k» для камер агрегации: k из scada-префикса Dev04(2k−1)/Dev04(2k).
     * Если поток у аппарата один — просто «Поток» (как «Поток» у Hassia/Bosch).
     */
    private static @NonNull String aggregationGroupLabel(
            @NonNull DeviceLayout layout,
            @NonNull DeviceEntry entry,
            @NonNull Map<String, String> scadaPrefixByCode
    ) {
        Integer stream = streamNumber(scadaPrefixByCode.get(entry.code()));
        long distinctStreams = layout.entries().stream()
                .filter(e -> e.code().startsWith("CamAgregation"))
                .map(e -> streamNumber(scadaPrefixByCode.get(e.code())))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        if (stream == null || distinctStreams <= 1) {
            return "Поток";
        }
        return "Поток " + stream;
    }

    /**
     * «Поток N» для обычных checker-камер (CamChecker[N]); без номера — «Поток».
     */
    private static @NonNull String checkerGroupLabel(@NonNull DeviceEntry entry) {
        Matcher matcher = CAM_CHECKER_NUMBER.matcher(entry.code());
        if (matcher.matches() && !matcher.group(1).isEmpty()) {
            return "Поток " + matcher.group(1);
        }
        return "Поток";
    }

    /**
     * Номер потока из scada-префикса: Dev041/Dev042 → 1, Dev043/Dev044 → 2.
     */
    private static @Nullable Integer streamNumber(@Nullable String scadaPrefix) {
        if (scadaPrefix == null) {
            return null;
        }
        Matcher matcher = DEV_PREFIX_NUMBER.matcher(scadaPrefix);
        if (!matcher.matches()) {
            return null;
        }
        int n = Integer.parseInt(matcher.group(1));
        if (n >= 41 && n % 2 == 1) {
            return (n - 39) / 2;
        }
        if (n >= 42 && n % 2 == 0) {
            return (n - 40) / 2;
        }
        return null;
    }
}
