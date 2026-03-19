package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties.DeviceNamesProperties;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties.InstanceProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Сервис runtime-discovery состава устройств аппарата.
 *
 * <p>Приоритет источников:
 * <ol>
 *   <li><b>Runtime</b> — поля {@code Level1Printers} и {@code LineDevices} из снапшота
 *       устройства {@code Line}.</li>
 *   <li><b>YAML fallback</b> — конфиг {@link PrintSrvProperties} (используется когда
 *       Line-снапшот ещё не получен или не содержит нужных полей).</li>
 * </ol>
 *
 * <p>Классификация устройств по имени:
 * <ul>
 *   <li>{@code Printer*} → printers</li>
 *   <li>{@code CamAgregationBox*} → aggregationBoxCams (проверяем до CamAgregation)</li>
 *   <li>{@code CamAgregation*} → aggregationCams</li>
 *   <li>{@code Cam*} → checkerCams (все остальные: CamChecker, CamEanChecker, CamBatch, …)</li>
 * </ul>
 *
 * <p>При расхождении runtime и YAML сервис логирует предупреждение, но продолжает работу.
 */
@Service
public class DeviceCompositionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCompositionService.class);

    private final InstanceSnapshotRepository snapshotRepo;
    private final Map<String, InstanceProperties> instancesById;

    public DeviceCompositionService(PrintSrvProperties config, InstanceSnapshotRepository snapshotRepo) {
        this.snapshotRepo = snapshotRepo;

        List<InstanceProperties> rawInstances = config.getInstances() == null
                ? List.of()
                : config.getInstances();

        Map<String, InstanceProperties> byId = new LinkedHashMap<>();
        for (InstanceProperties inst : rawInstances) {
            if (inst == null) {
                continue;
            }

            String id = inst.getId();
            if (id == null || id.isBlank()) {
                log.warn("Skipping PrintSrv instance with empty id in configuration: {}", inst);
                continue;
            }

            byId.putIfAbsent(id, inst);
        }

        this.instancesById = Map.copyOf(byId);
    }

    /**
     * Строит состав непосредственно из YAML-конфига.
     */
    private static @NonNull DeviceComposition fromConfig(@NonNull DeviceNamesProperties d) {
        return new DeviceComposition(
                List.copyOf(d.getPrinters()),
                List.copyOf(d.getAggregationCams()),
                List.copyOf(d.getAggregationBoxCams()),
                List.copyOf(d.getCheckerCams())
        );
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Разбирает строку вида {@code "Device1, Device2, Device3"} в список имён.
     * Пустые части и пробелы игнорируются.
     */
    private static @NonNull List<String> parseCommaList(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Логирует расхождение между runtime discovery и YAML (не блокирует).
     */
    private static void logDiscrepancyIfNeeded(
            String instanceId,
            DeviceNamesProperties yamlDevices,
            List<String> runtimePrinters,
            List<String> runtimeAggr,
            List<String> runtimeAggrBox,
            List<String> runtimeCheckers
    ) {
        if (!new HashSet<>(yamlDevices.getPrinters()).equals(new HashSet<>(runtimePrinters))) {
            log.warn("[{}] Device composition mismatch — printers: YAML={}, runtime={}",
                    instanceId, yamlDevices.getPrinters(), runtimePrinters);
        }
        if (!new HashSet<>(yamlDevices.getAggregationCams()).equals(new HashSet<>(runtimeAggr))) {
            log.warn("[{}] Device composition mismatch — aggregationCams: YAML={}, runtime={}",
                    instanceId, yamlDevices.getAggregationCams(), runtimeAggr);
        }
        if (!new HashSet<>(yamlDevices.getAggregationBoxCams()).equals(new HashSet<>(runtimeAggrBox))) {
            log.warn("[{}] Device composition mismatch — aggregationBoxCams: YAML={}, runtime={}",
                    instanceId, yamlDevices.getAggregationBoxCams(), runtimeAggrBox);
        }
        if (!new HashSet<>(yamlDevices.getCheckerCams()).equals(new HashSet<>(runtimeCheckers))) {
            log.warn("[{}] Device composition mismatch — checkerCams: YAML={}, runtime={}",
                    instanceId, yamlDevices.getCheckerCams(), runtimeCheckers);
        }
    }

    /**
     * Возвращает состав устройств для инстанса.
     * Если Line-снапшот доступен и содержит оба поля — используется runtime-discovery.
     * Иначе — конфиг.
     *
     * @param instanceId идентификатор аппарата
     * @return состав устройств (никогда не null; пустой если инстанс неизвестен)
     */
    public @NonNull DeviceComposition getComposition(@NonNull String instanceId) {
        InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) {
            return DeviceComposition.empty();
        }

        DeviceComposition runtime = fromLineSnapshot(instanceId, inst);
        if (runtime != null) {
            return runtime;
        }

        log.debug("[{}] Line snapshot not yet available — using YAML config for device composition",
                instanceId);
        return fromConfig(inst.getDevices());
    }

    /**
     * Строит состав из полей {@code Level1Printers} и {@code LineDevices} снапшота Line.
     * Возвращает {@code null}, если снапшот недоступен или поля отсутствуют.
     */
    private @Nullable DeviceComposition fromLineSnapshot(
            @NonNull String instanceId,
            @NonNull InstanceProperties inst
    ) {
        DeviceSnapshot lineSnap = snapshotRepo.get(instanceId, inst.getDevices().getLine());
        if (lineSnap == null || lineSnap.units().isEmpty()) {
            return null;
        }

        UnitSnapshot firstUnit = lineSnap.units().values().iterator().next();
        Optional<String> level1PrintersOpt = firstUnit.properties().getLevel1Printers();
        Optional<String> lineDevicesOpt = firstUnit.properties().getLineDevices();

        if (level1PrintersOpt.isEmpty() || lineDevicesOpt.isEmpty()) {
            return null;
        }

        List<String> printers = parseCommaList(level1PrintersOpt.get());
        List<String> allDevices = parseCommaList(lineDevicesOpt.get());

        if (printers.isEmpty() && allDevices.isEmpty()) {
            return null;
        }

        Set<String> printerSet = new HashSet<>(printers);

        List<String> aggregationCams = new ArrayList<>();
        List<String> aggregationBoxCams = new ArrayList<>();
        List<String> checkerCams = new ArrayList<>();

        for (String device : allDevices) {
            if (printerSet.contains(device)) {
                continue; // Already captured in printers list
            }
            if (device.startsWith("CamAgregationBox")) {
                aggregationBoxCams.add(device);
            } else if (device.startsWith("CamAgregation")) {
                aggregationCams.add(device);
            } else if (device.startsWith("Cam")) {
                checkerCams.add(device);
            } else {
                log.debug("[{}] Unrecognised device type in LineDevices, ignored: {}", instanceId, device);
            }
        }

        // Log discrepancy with YAML config (informational, not blocking)
        logDiscrepancyIfNeeded(instanceId, inst.getDevices(), printers, aggregationCams,
                aggregationBoxCams, checkerCams);

        return new DeviceComposition(
                List.copyOf(printers),
                List.copyOf(aggregationCams),
                List.copyOf(aggregationBoxCams),
                List.copyOf(checkerCams)
        );
    }
}
