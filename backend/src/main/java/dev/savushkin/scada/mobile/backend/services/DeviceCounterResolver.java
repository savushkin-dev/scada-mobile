package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceEntry;
import dev.savushkin.scada.mobile.backend.services.DeviceScadaRegistry.DeviceLayout;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static dev.savushkin.scada.mobile.backend.services.RuntimeTagMapper.CounterResolution;

/**
 * Единая точка разрешения счётчиков «Считано/Несчитано» устройств.
 *
 * <p>Правило чтения — scada-first ({@code Dev0XXCounterGeneral/CounterMissing}),
 * fallback на поля устройства {@code Succeeded/Failed} (см. {@link RuntimeTagMapper}).
 *
 * <p>Агрегат для карточки автомата: первая по {@code display_order} камера
 * с {@code show_counters=true}; fallback — камеры агрегации. EAN/checker-камеры
 * без флага не участвуют (старая SCADA счётчики им не показывает).
 */
@Service
public class DeviceCounterResolver {

    private static final Logger log = LoggerFactory.getLogger(DeviceCounterResolver.class);

    private final PrintSrvTopologyRepository topologyRepo;
    private final InstanceSnapshotRepository snapshotRepo;
    private final DeviceScadaRegistry deviceScadaRegistry;
    private final DeviceCompositionService compositionService;

    public DeviceCounterResolver(PrintSrvTopologyRepository topologyRepo,
                                 InstanceSnapshotRepository snapshotRepo,
                                 DeviceScadaRegistry deviceScadaRegistry,
                                 DeviceCompositionService compositionService) {
        this.topologyRepo = topologyRepo;
        this.snapshotRepo = snapshotRepo;
        this.deviceScadaRegistry = deviceScadaRegistry;
        this.compositionService = compositionService;
    }

    /**
     * Результат агрегации счётчиков для карточки автомата.
     */
    public record UnitCounters(@Nullable String read, @Nullable String unread) {
    }

    /**
     * Разрешает счётчики одного устройства: scada CounterGeneral/CounterMissing,
     * fallback на Succeeded/Failed устройства.
     *
     * @return resolved-значения или {@code null}, если снапшот устройства недоступен
     */
    public @Nullable CounterResolution resolve(@NonNull String instanceId,
                                                                @NonNull String deviceCode) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) {
            return null;
        }
        Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, deviceCode));
        if (camRaw.isEmpty()) {
            return null;
        }
        Map<String, String> scadaRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, inst.scadaDeviceName()));
        RuntimeTagMapper.CounterResolution resolved = RuntimeTagMapper.resolveCounters(
                camRaw, scadaRaw, deviceScadaRegistry.resolveScadaPrefix(instanceId, deviceCode));
        log.debug("[{}] Camera {} counters: read={} ({}), unread={} ({})",
                instanceId, deviceCode, resolved.read(), resolved.readSource(),
                resolved.unread(), resolved.unreadSource());
        return resolved;
    }

    /**
     * Агрегированные счётчики для карточки автомата (UNITS_STATUS/LINE_STATUS).
     *
     * <p>Кандидаты: устройства с {@code show_counters=true} по {@code display_order},
     * затем камеры агрегации. Далее — первая камера с ненулевым read,
     * иначе с ненулевым unread, иначе первая доступная; при полном отсутствии — ("0", "0").
     */
    public @NonNull UnitCounters resolveUnitCounters(@NonNull String instanceId) {
        List<String> candidates = counterCandidates(instanceId);
        if (candidates.isEmpty()) {
            return new UnitCounters("0", "0");
        }

        for (String candidate : candidates) {
            RuntimeTagMapper.CounterResolution counters = resolve(instanceId, candidate);
            if (counters != null && isNonZero(counters.read())) {
                return new UnitCounters(counters.read(), counters.unread());
            }
        }
        for (String candidate : candidates) {
            RuntimeTagMapper.CounterResolution counters = resolve(instanceId, candidate);
            if (counters != null && isNonZero(counters.unread())) {
                return new UnitCounters(counters.read(), counters.unread());
            }
        }
        for (String candidate : candidates) {
            RuntimeTagMapper.CounterResolution counters = resolve(instanceId, candidate);
            if (counters != null) {
                return new UnitCounters(counters.read(), counters.unread());
            }
        }
        return new UnitCounters("0", "0");
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    /**
     * Кандидаты на агрегированные счётчики: устройства с show_counters
     * (по display_order). Состав аппарата используется только когда у аппарата
     * вообще нет записей устройств — тогда явный запрет счётчиков считаем намеренным.
     */
    private @NonNull List<String> counterCandidates(@NonNull String instanceId) {
        DeviceLayout layout = deviceScadaRegistry.loadLayout(instanceId);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        layout.entries().stream()
                .filter(DeviceEntry::showCounters)
                .sorted(Comparator.comparingInt(DeviceEntry::displayOrder).thenComparing(DeviceEntry::code))
                .map(DeviceEntry::code)
                .forEach(candidates::add);
        if (candidates.isEmpty() && layout.entries().isEmpty()) {
            DeviceComposition composition = compositionService.getComposition(instanceId);
            candidates.addAll(composition.aggregationCams());
        }
        return List.copyOf(candidates);
    }

    /**
     * Проверяет, что строковое значение счётчика не null, не пустое и не равно нулю.
     * Учитывает форматы "0", "0.0", "0,0".
     */
    private static boolean isNonZero(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().replace(',', '.');
        try {
            return Double.parseDouble(normalized) != 0.0d;
        } catch (NumberFormatException e) {
            // Нечисловое значение считаем ненулевым
            return true;
        }
    }

    private static @NonNull Map<String, String> firstUnitRawProperties(@Nullable DeviceSnapshot snapshot) {
        if (snapshot == null || snapshot.units().isEmpty()) {
            return Collections.emptyMap();
        }
        return snapshot.units().values().iterator().next().properties().getRawProperties();
    }
}
