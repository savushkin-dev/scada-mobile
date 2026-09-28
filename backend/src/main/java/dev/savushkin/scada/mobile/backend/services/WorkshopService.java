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
    private final CurItemResolver curItemResolver;
    private final DeviceScadaRegistry deviceScadaRegistry;
    private final DeviceGroupService deviceGroupService;
    private final DeviceCounterResolver deviceCounterResolver;

    public WorkshopService(PrintSrvTopologyRepository topologyRepo,
                           InstanceSnapshotRepository snapshotRepo,
                           DeviceCompositionService deviceCompositionService,
                           UnitErrorStore unitErrorStore,
                           CurItemResolver curItemResolver,
                           DeviceScadaRegistry deviceScadaRegistry,
                           DeviceGroupService deviceGroupService,
                           DeviceCounterResolver deviceCounterResolver) {
        this.topologyRepo = topologyRepo;
        this.snapshotRepo = snapshotRepo;
        this.deviceCompositionService = deviceCompositionService;
        this.unitErrorStore = unitErrorStore;
        this.curItemResolver = curItemResolver;
        this.deviceScadaRegistry = deviceScadaRegistry;
        this.deviceGroupService = deviceGroupService;
        this.deviceCounterResolver = deviceCounterResolver;
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

        // Per-unit раскладка: группы устройств и мета (имена, счётчики)
        DeviceScadaRegistry.DeviceLayout layout = deviceScadaRegistry.loadLayout(instanceId);
        Map<String, String> scadaPrefixByCode = new LinkedHashMap<>();
        Set<String> hiddenCodes = new HashSet<>();
        for (DeviceScadaRegistry.DeviceEntry entry : layout.entries()) {
            if (entry.hidden()) {
                hiddenCodes.add(entry.code());
            }
            String prefix = deviceScadaRegistry.resolveScadaPrefix(instanceId, entry.code());
            if (prefix != null) {
                scadaPrefixByCode.put(entry.code(), prefix);
            }
        }

        // Скрытые устройства не попадают ни в один вид топологии (legacy-массивы,
        // имена, группы, мета) — флаг влияет только на отображение, опрос идёт.
        Map<String, String> displayNames = new LinkedHashMap<>(inst.deviceDisplayNames());
        displayNames.keySet().removeAll(hiddenCodes);

        // Live-обогащение меты: текущая партия устройства из runtime-тега curitem.
        // Устройства без тега (или без снапшота) получают null.
        Map<String, DeviceMetaDTO> deviceMeta = new LinkedHashMap<>();
        for (Map.Entry<String, DeviceMetaDTO> e
                : deviceGroupService.buildDeviceMeta(layout).entrySet()) {
            String currentBatch = firstUnitNamedProp(snapshotRepo.get(instanceId, e.getKey()),
                    u -> u.properties().getCurItem().orElse(null));
            deviceMeta.put(e.getKey(), new DeviceMetaDTO(
                    e.getValue().displayName(), e.getValue().showCounters(), currentBatch));
        }

        return Optional.of(new UnitDeviceTopologyDTO(
                inst.instanceId(),
                inst.workshopId(),
                resolveUnitName(inst),
                new DeviceGroupsDTO(
                        withoutHidden(composition.printers(), hiddenCodes),
                        withoutHidden(composition.aggregationCams(), hiddenCodes),
                        withoutHidden(composition.aggregationBoxCams(), hiddenCodes),
                        withoutHidden(composition.checkerCams(), hiddenCodes)
                ),
                displayNames,
                inst.typeDisplayNames(),
                deviceGroupService.buildGroups(layout, scadaPrefixByCode),
                deviceMeta
        ));
    }

    private static @NonNull List<String> withoutHidden(@NonNull List<String> codes, @NonNull Set<String> hiddenCodes) {
        if (hiddenCodes.isEmpty()) {
            return codes;
        }
        return codes.stream().filter(c -> !hiddenCodes.contains(c)).toList();
    }

    /**
     * Извлекает именованное поле из первого юнита снапшота устройства.
     */
    private static @Nullable String firstUnitNamedProp(
            @Nullable DeviceSnapshot snapshot,
            java.util.function.Function<UnitSnapshot, @Nullable String> extractor
    ) {
        if (snapshot == null || snapshot.units().isEmpty()) {
            return null;
        }
        return extractor.apply(snapshot.units().values().iterator().next());
    }

    /**
     * Возвращает live-статус аппаратов цеха (событие).
     *
     * @param workshopId идентификатор цеха
     */
    public List<UnitStatusDTO> getUnitsStatus(long workshopId) {
        return topologyRepo.findAllActiveInstances().stream()
                .filter(inst -> inst.workshopId() == workshopId)
                .map(inst -> {
                    String instanceId = inst.instanceId();
                    DeviceCounterResolver.UnitCounters counters = deviceCounterResolver.resolveUnitCounters(instanceId);
                    return new UnitStatusDTO(
                            instanceId,
                            inst.workshopId(),
                            deriveEvent(instanceId),
                            counters.read(),
                            counters.unread()
                    );
                })
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

        DeviceCounterResolver.UnitCounters counters = deviceCounterResolver.resolveUnitCounters(instanceId);
        return Optional.of(new UnitStatusDTO(
                inst.instanceId(),
                inst.workshopId(),
                deriveEvent(inst.instanceId()),
                counters.read(),
                counters.unread()
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

        String curItem = curItemResolver.resolveCurItem(instanceId);
        return curItem != null ? curItem : "Нет данных";
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
