package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Резолвер текущего значения {@code CurItem} аппарата.
 * <p>
 * Единая точка получения «текущей партии/изделия»: сначала снапшот Line,
 * затем первый принтер из состава устройств. Используется {@link WorkshopService}
 * (поле {@code event} в статусе аппарата) и {@link NotificationService}
 * (фиксация партии в уведомлении «последняя партия»).
 */
@Component
public class CurItemResolver {

    private final PrintSrvTopologyRepository topologyRepo;
    private final InstanceSnapshotRepository snapshotRepo;
    private final DeviceCompositionService deviceCompositionService;

    public CurItemResolver(PrintSrvTopologyRepository topologyRepo,
                           InstanceSnapshotRepository snapshotRepo,
                           DeviceCompositionService deviceCompositionService) {
        this.topologyRepo = topologyRepo;
        this.snapshotRepo = snapshotRepo;
        this.deviceCompositionService = deviceCompositionService;
    }

    /**
     * Возвращает текущее значение CurItem аппарата или {@code null},
     * если аппарат неизвестен либо значение недоступно/пустое.
     */
    public @Nullable String resolveCurItem(@NonNull String instanceId) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) {
            return null;
        }

        DeviceSnapshot lineSnapshot = findSnapshotByDeviceName(instanceId, inst.lineDeviceName());
        String lineCurItem = extractCurItem(lineSnapshot);
        if (lineCurItem != null) {
            return lineCurItem;
        }

        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        if (!composition.printers().isEmpty()) {
            String firstPrinter = composition.printers().getFirst();
            DeviceSnapshot printerSnapshot = findSnapshotByDeviceName(instanceId, firstPrinter);
            String printerCurItem = extractCurItem(printerSnapshot);
            if (printerCurItem != null) {
                return printerCurItem;
            }
        }

        return null;
    }

    private static @Nullable String extractCurItem(@Nullable DeviceSnapshot snapshot) {
        if (snapshot == null || snapshot.units().isEmpty()) {
            return null;
        }
        UnitSnapshot unit = snapshot.units().values().iterator().next();
        String value = unit.properties().getCurItem().orElse(null);
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Ищет snapshot устройства сначала по точному имени, затем case-insensitive.
     * Это защищает от вариаций регистра имён устройств у разных PrintSrv.
     */
    private @Nullable DeviceSnapshot findSnapshotByDeviceName(@NonNull String instanceId, @NonNull String deviceName) {
        DeviceSnapshot exact = snapshotRepo.get(instanceId, deviceName);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, DeviceSnapshot> entry : snapshotRepo.getAllForInstance(instanceId).entrySet()) {
            if (entry.getKey().equalsIgnoreCase(deviceName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
