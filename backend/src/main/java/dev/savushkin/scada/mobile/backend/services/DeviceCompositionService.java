package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
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
 *   <li><b>БД fallback</b> — конфигурация из {@link PrintSrvTopologyRepository} (используется когда
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
 * <p>При расхождении runtime и БД сервис логирует предупреждение, но продолжает работу.
 */
@Service
public class DeviceCompositionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCompositionService.class);

    private final InstanceSnapshotRepository snapshotRepo;
    private final PrintSrvTopologyRepository topologyRepo;

    public DeviceCompositionService(InstanceSnapshotRepository snapshotRepo,
                                    PrintSrvTopologyRepository topologyRepo) {
        this.snapshotRepo = snapshotRepo;
        this.topologyRepo = topologyRepo;
    }

    /**
     * Строит состав непосредственно из БД.
     */
    private static @NonNull DeviceComposition fromDb(@NonNull PrintSrvInstance inst) {
        return new DeviceComposition(
                List.copyOf(inst.printers()),
                List.copyOf(inst.aggregationCams()),
                List.copyOf(inst.aggregationBoxCams()),
                List.copyOf(inst.checkerCams())
        );
    }

    /**
     * Возвращает runtime-состав устройств из снапшота Line.
     * Возвращает {@code null}, если снапшот недоступен или не содержит нужных полей.
     *
     * @param instanceId идентификатор аппарата
     * @return runtime-состав или null
     */
    public @Nullable DeviceComposition getRuntimeComposition(@NonNull String instanceId) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) {
            return null;
        }
        return fromLineSnapshot(instanceId, inst);
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
     * Логирует расхождение между runtime discovery и БД (не блокирует).
     */
    private static void logDiscrepancyIfNeeded(
            String instanceId,
            PrintSrvInstance inst,
            List<String> runtimePrinters,
            List<String> runtimeAggr,
            List<String> runtimeAggrBox,
            List<String> runtimeCheckers
    ) {
        if (!new HashSet<>(inst.printers()).equals(new HashSet<>(runtimePrinters))) {
            log.warn("[{}] Device composition mismatch — printers: DB={}, runtime={}",
                    instanceId, inst.printers(), runtimePrinters);
        }
        if (!new HashSet<>(inst.aggregationCams()).equals(new HashSet<>(runtimeAggr))) {
            log.warn("[{}] Device composition mismatch — aggregationCams: DB={}, runtime={}",
                    instanceId, inst.aggregationCams(), runtimeAggr);
        }
        if (!new HashSet<>(inst.aggregationBoxCams()).equals(new HashSet<>(runtimeAggrBox))) {
            log.warn("[{}] Device composition mismatch — aggregationBoxCams: DB={}, runtime={}",
                    instanceId, inst.aggregationBoxCams(), runtimeAggrBox);
        }
        if (!new HashSet<>(inst.checkerCams()).equals(new HashSet<>(runtimeCheckers))) {
            log.warn("[{}] Device composition mismatch — checkerCams: DB={}, runtime={}",
                    instanceId, inst.checkerCams(), runtimeCheckers);
        }
    }

    /**
     * Возвращает состав устройств для инстанса.
     * Если Line-снапшот доступен и содержит оба поля — используется runtime-discovery.
     * Иначе — БД как fallback.
     *
     * @param instanceId идентификатор аппарата
     * @return состав устройств (никогда не null; пустой если инстанс неизвестен)
     */
    public @NonNull DeviceComposition getComposition(@NonNull String instanceId) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) {
            return DeviceComposition.empty();
        }

        DeviceComposition runtime = fromLineSnapshot(instanceId, inst);
        if (runtime != null) {
            return runtime;
        }

        log.debug("[{}] Line snapshot not yet available — using DB config for device composition",
                instanceId);
        return fromDb(inst);
    }

    /**
     * Строит состав из полей {@code Level1Printers} и {@code LineDevices} снапшота Line.
     * Возвращает {@code null}, если снапшот недоступен или поля отсутствуют.
     */
    private @Nullable DeviceComposition fromLineSnapshot(
            @NonNull String instanceId,
            @NonNull PrintSrvInstance inst
    ) {
        DeviceSnapshot lineSnap = snapshotRepo.get(instanceId, inst.lineDeviceName());
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

        // Log discrepancy with DB config (informational, not blocking)
        logDiscrepancyIfNeeded(instanceId, inst, printers, aggregationCams,
                aggregationBoxCams, checkerCams);

        return new DeviceComposition(
                List.copyOf(printers),
                List.copyOf(aggregationCams),
                List.copyOf(aggregationBoxCams),
                List.copyOf(checkerCams)
        );
    }
}
