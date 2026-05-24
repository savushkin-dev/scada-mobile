package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.*;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.Workshop;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для формирования данных REST API цехов и аппаратов.
 * <p>
 * Объединяет статическую конфигурацию из БД (список цехов и аппаратов)
 * с live-данными из {@link InstanceSnapshotRepository} (текущее состояние).
 * <p>
 * Данные разделены на два слоя:
 * <ul>
 *   <li><b>Topology</b> — статика из БД, меняется крайне редко.
 *       Возвращается REST-эндпоинтами с поддержкой ETag-кэширования.</li>
 *   <li><b>Status</b> — live-данные из snapshot store.
 *       Рассылается по WebSocket по мере готовности конкретных аппаратов.</li>
 * </ul>
 */
@Service
public class WorkshopService {

    private static final Logger log = LoggerFactory.getLogger(WorkshopService.class);

    private final PrintSrvTopologyRepository topologyRepo;
    private final InstanceSnapshotRepository snapshotRepo;
    private final DeviceCompositionService deviceCompositionService;
    private final UnitErrorStore unitErrorStore;

    public WorkshopService(PrintSrvTopologyRepository topologyRepo,
                           InstanceSnapshotRepository snapshotRepo,
                           DeviceCompositionService deviceCompositionService,
                           UnitErrorStore unitErrorStore) {
        this.topologyRepo = topologyRepo;
        this.snapshotRepo = snapshotRepo;
        this.deviceCompositionService = deviceCompositionService;
        this.unitErrorStore = unitErrorStore;
        log.info("WorkshopService initialized");
    }

    // ─── Topology (статика, кэшируется на клиенте) ────────────────────────────

    /**
     * Возвращает предвычисленный ETag конфигурации топологии.
     * <p>
     * Значение — SHA-256-хэш в формате {@code "hex-string"},
     * готовый для вставки в заголовок {@code ETag}.
     */
    public String getConfigETag() {
        return topologyRepo.getConfigETag();
    }

    /**
     * Возвращает статическую топологию всех цехов.
     * Не содержит live-данных — пригоден для длительного кэширования.
     */
    public List<WorkshopTopologyDTO> getWorkshopsTopology() {
        Map<Long, List<PrintSrvInstance>> instancesByWorkshop = topologyRepo.findAllActiveInstances().stream()
                .collect(Collectors.groupingBy(
                        PrintSrvInstance::workshopId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return topologyRepo.findAllActiveWorkshops().stream()
                .map(ws -> new WorkshopTopologyDTO(
                        ws.id(),
                        ws.displayName(),
                        instancesByWorkshop.getOrDefault(ws.id(), Collections.emptyList()).size()
                ))
                .toList();
    }

    // ─── Status (live, рассылается по WebSocket) ──────────────────────────────

    /**
     * Возвращает статическую топологию аппаратов цеха.
     *
     * @param workshopId идентификатор цеха
     * @return список аппаратов или пустой список, если цех не найден
     */
    public List<UnitTopologyDTO> getUnitsTopology(long workshopId) {
        return topologyRepo.findAllActiveInstances().stream()
                .filter(inst -> inst.workshopId() == workshopId)
                .map(inst -> new UnitTopologyDTO(inst.instanceId(), inst.workshopId(), resolveUnitName(inst)))
                .toList();
    }

    /**
     * Возвращает топологию устройств конкретного аппарата.
     * <p>
     * Источник данных: runtime-discovery из снапшота Line (если доступен),
     * иначе — БД как fallback.
     * <p>
     * Дополнительно проверяет принадлежность аппарата указанному цеху:
     * если {@code instanceId} существует, но относится к другому цеху,
     * метод возвращает в {@link Optional#empty()}.
     *
     * @param workshopId идентификатор цеха
     * @param instanceId идентификатор аппарата
     * @return DTO с группами устройств, или {@link Optional#empty()} если не найден
     */
    public Optional<UnitDeviceTopologyDTO> getUnitDeviceTopology(long workshopId, String instanceId) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null || inst.workshopId() != workshopId) {
            return Optional.empty();
        }
        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        return Optional.of(new UnitDeviceTopologyDTO(
                inst.instanceId(),
                inst.workshopId(),
                resolveUnitName(inst),
                new DeviceGroupsDTO(
                        composition.printers(),
                        composition.aggregationCams(),
                        composition.aggregationBoxCams(),
                        composition.checkerCams()
                )
        ));
    }

    /**
     * Возвращает live-статус аппаратов цеха (событие).
     *
     * @param workshopId идентификатор цеха
     */
    public List<UnitStatusDTO> getUnitsStatus(long workshopId) {
        return topologyRepo.findAllActiveInstances().stream()
                .filter(inst -> inst.workshopId() == workshopId)
                .map(inst -> new UnitStatusDTO(
                        inst.instanceId(),
                        inst.workshopId(),
                        deriveEvent(inst.instanceId())
                ))
                .toList();
    }

    /**
     * Возвращает live-статус только одного аппарата.
     */
    public Optional<UnitStatusDTO> getUnitStatus(String instanceId) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) {
            return Optional.empty();
        }

        return Optional.of(new UnitStatusDTO(
                inst.instanceId(),
                inst.workshopId(),
                deriveEvent(inst.instanceId())
        ));
    }

    /**
     * Проверяет, существует ли цех с заданным id.
     */
    public boolean workshopExists(long workshopId) {
        return topologyRepo.findAllActiveWorkshops().stream()
                .anyMatch(ws -> ws.id() == workshopId);
    }

    public Optional<Long> getWorkshopIdForInstance(String instanceId) {
        return topologyRepo.findByInstanceId(instanceId)
                .map(PrintSrvInstance::workshopId);
    }

    // ─── Внутренние методы формирования live-данных ─────────────────────────

    /**
     * Определяет, есть ли активная ошибка на инстансе.
     * Проверяет наличие ошибок в {@link UnitErrorStore}.
     */
    private boolean hasActiveError(String instanceId) {
        return unitErrorStore.hasErrors(instanceId);
    }

    private int countProblemUnits(@NonNull List<PrintSrvInstance> instances) {
        int count = 0;
        for (PrintSrvInstance inst : instances) {
            if (hasActiveError(inst.instanceId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Формирует текстовое описание текущего события для аппарата.
     * При наличии ошибок возвращает список "DEVICE: message".
     * При отсутствии ошибок возвращает curItem.
     */
    private @NonNull String deriveEvent(String instanceId) {
        List<DeviceError> errors = unitErrorStore.getErrors(instanceId);
        if (!errors.isEmpty()) {
            return formatErrorEvent(errors);
        }

        String curItem = resolveCurItem(instanceId);
        return curItem != null ? curItem : "Нет данных";
    }

    private @Nullable String resolveCurItem(@NonNull String instanceId) {
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
        return nullIfBlank(unit.properties().getCurItem().orElse(null));
    }

    private static @NonNull String formatErrorEvent(@NonNull List<DeviceError> errors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            DeviceError error = errors.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            String message = nullIfBlank(error.description());
            if (message == null) {
                message = nullIfBlank(error.propertyDesc());
            }
            sb.append(error.objectName()).append(": ").append(message == null ? "" : message);
        }
        return sb.toString();
    }

    private static @Nullable String nullIfBlank(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private @NonNull String getLineDeviceName(@NonNull String instanceId) {
        return topologyRepo.findByInstanceId(instanceId)
                .map(PrintSrvInstance::lineDeviceName)
                .orElse("Line");
    }

    /**
     * Ищет snapshot устройства сначала по точному имени, затем case-insensitive.
     * Это защищает API-слой от вариаций регистра имён устройств у разных PrintSrv.
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

    /**
     * Защитный fallback для API-контракта topology:
     * если displayName не задан, отдаем id.
     */
    private @NonNull String resolveUnitName(PrintSrvInstance inst) {
        String displayName = inst.displayName();
        if (displayName == null || displayName.isBlank()) {
            return inst.instanceId();
        }
        return displayName;
    }
}
