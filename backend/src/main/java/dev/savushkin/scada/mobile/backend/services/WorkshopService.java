package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.*;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для формирования данных REST API цехов и аппаратов.
 * <p>
 * Объединяет статическую конфигурацию из YAML (список цехов и аппаратов)
 * с live-данными из {@link InstanceSnapshotRepository} (текущее состояние).
 * <p>
 * Данные разделены на два слоя:
 * <ul>
 *   <li><b>Topology</b> — статика из конфига, меняется крайне редко.
 *       Возвращается REST-эндпоинтами с поддержкой ETag-кэширования.</li>
 *   <li><b>Status</b> — live-данные из snapshot store.
 *       Рассылается по WebSocket по мере готовности конкретных аппаратов.</li>
 * </ul>
 */
@Service
public class WorkshopService {

    private static final Logger log = LoggerFactory.getLogger(WorkshopService.class);

    private final PrintSrvProperties config;
    private final InstanceSnapshotRepository snapshotRepo;
    private final DeviceCompositionService deviceCompositionService;
    private final UnitErrorStore unitErrorStore;

    /** Быстрый lookup: workshopId → список инстансов. */
    private final Map<String, List<PrintSrvProperties.InstanceProperties>> instancesByWorkshop;
    /**
     * Быстрый lookup: instanceId → конфиг инстанса.
     */
    private final Map<String, PrintSrvProperties.InstanceProperties> instancesById;

    /**
     * ETag для topology-эндпоинтов. Вычисляется один раз при старте
     * как SHA-256 от конфигурации цехов и инстансов.
     * Контроллер проверяет {@code If-None-Match} против этого значения
     * и возвращает {@code 304 Not Modified} при совпадении.
     */
    private final String configETag;

    public WorkshopService(PrintSrvProperties config,
                           InstanceSnapshotRepository snapshotRepo,
                           DeviceCompositionService deviceCompositionService,
                           UnitErrorStore unitErrorStore) {
        this.config = config;
        this.snapshotRepo = snapshotRepo;
        this.deviceCompositionService = deviceCompositionService;
        this.unitErrorStore = unitErrorStore;
        this.instancesByWorkshop = config.getInstances().stream()
                .collect(Collectors.groupingBy(
                        PrintSrvProperties.InstanceProperties::getWorkshopId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        this.instancesById = config.getInstances().stream()
                .collect(Collectors.toMap(
                        PrintSrvProperties.InstanceProperties::getId,
                        inst -> inst,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        this.configETag = computeConfigETag(config);
        log.info("WorkshopService initialized: {} workshops, {} instances, ETag={}",
                config.getWorkshops().size(), config.getInstances().size(), configETag);
    }

    // ─── Topology (статика, кэшируется на клиенте) ────────────────────────────

    /**
     * Вычисляет SHA-256 хэш конфигурации топологии при инициализации сервиса.
     * <p>
     * Входные данные: отсортированный список "workshopId:displayName" + "instanceId:workshopId:displayName".
     * Сортировка гарантирует детерминированный результат при любом порядке в YAML.
     * <p>
     * Формат результата: hex-строка без кавычек (кавычки добавит контроллер для заголовка ETag).
     */
    private static @NonNull String computeConfigETag(@NonNull PrintSrvProperties config) {
        try {
            StringBuilder sb = new StringBuilder();
            config.getWorkshops().stream()
                    .sorted(Comparator.comparing(PrintSrvProperties.WorkshopProperties::getId))
                    .forEach(ws -> sb.append("w:").append(ws.getId()).append(':').append(ws.getDisplayName()).append(';'));
            config.getInstances().stream()
                    .sorted(Comparator.comparing(PrintSrvProperties.InstanceProperties::getId))
                    .forEach(inst -> sb.append("i:").append(inst.getId()).append(':')
                            .append(inst.getWorkshopId()).append(':').append(inst.getDisplayName()).append(':')
                            .append(String.join(",", inst.getAllDeviceNames())).append(';'));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 гарантированно доступен в JDK — этот путь не достижим
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Возвращает статическую топологию всех цехов.
     * Не содержит live-данных — пригоден для длительного кэширования.
     */
    public List<WorkshopTopologyDTO> getWorkshopsTopology() {
        return config.getWorkshops().stream()
                .map(ws -> new WorkshopTopologyDTO(
                        ws.getId(),
                        ws.getDisplayName(),
                        instancesByWorkshop.getOrDefault(ws.getId(), Collections.emptyList()).size()
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
    public List<UnitTopologyDTO> getUnitsTopology(String workshopId) {
        return instancesByWorkshop.getOrDefault(workshopId, Collections.emptyList())
                .stream()
                .map(inst -> new UnitTopologyDTO(inst.getId(), inst.getWorkshopId(), resolveUnitName(inst)))
                .toList();
    }

    /**
     * Возвращает топологию устройств конкретного аппарата.
     * <p>
     * Источник данных: runtime-discovery из снапшота Line (если доступен),
     * иначе — YAML-конфиг как fallback.
     * <p>
     * Дополнительно проверяет принадлежность аппарата указанному цеху:
     * если {@code instanceId} существует, но относится к другому цеху,
     * метод возвращает в {@link Optional#empty()}.
     *
     * @param workshopId идентификатор цеха
     * @param instanceId идентификатор аппарата
     * @return DTO с группами устройств, или {@link Optional#empty()} если не найден
     */
    public Optional<UnitDeviceTopologyDTO> getUnitDeviceTopology(String workshopId, String instanceId) {
        PrintSrvProperties.InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null || !inst.getWorkshopId().equals(workshopId)) {
            return Optional.empty();
        }
        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        return Optional.of(new UnitDeviceTopologyDTO(
                inst.getId(),
                inst.getWorkshopId(),
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
    public List<UnitStatusDTO> getUnitsStatus(String workshopId) {
        return instancesByWorkshop.getOrDefault(workshopId, Collections.emptyList())
                .stream()
                .map(inst -> new UnitStatusDTO(
                        inst.getId(),
                        inst.getWorkshopId(),
                        deriveEvent(inst.getId())
                ))
                .toList();
    }

    /**
     * Возвращает live-статус только одного аппарата.
     */
    public Optional<UnitStatusDTO> getUnitStatus(String instanceId) {
        PrintSrvProperties.InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) {
            return Optional.empty();
        }

        return Optional.of(new UnitStatusDTO(
                inst.getId(),
                inst.getWorkshopId(),
                deriveEvent(inst.getId())
        ));
    }

    /**
     * Проверяет, существует ли цех с заданным id.
     */
    public boolean workshopExists(String workshopId) {
        return config.getWorkshops().stream()
                .anyMatch(ws -> ws.getId().equals(workshopId));
    }

    public Optional<String> getWorkshopIdForInstance(String instanceId) {
        return Optional.ofNullable(instancesById.get(instanceId))
                .map(PrintSrvProperties.InstanceProperties::getWorkshopId);
    }

    // ─── Внутренние методы формирования live-данных ───────────────────────────

    /**
     * Возвращает предвычисленный ETag конфигурации топологии.
     * Значение — SHA-256-хэш в формате {@code "hex-string"},
     * готовый для вставки в заголовок {@code ETag}.
     */
    public String getConfigETag() {
        return configETag;
    }

    /**
     * Определяет, есть ли активная ошибка на инстансе.
        * Проверяет наличие ошибок в {@link UnitErrorStore}.
     */
    private boolean hasActiveError(String instanceId) {
        return unitErrorStore.hasErrors(instanceId);
    }

    private int countProblemUnits(@NonNull List<PrintSrvProperties.InstanceProperties> instances) {
        int count = 0;
        for (PrintSrvProperties.InstanceProperties inst : instances) {
            if (hasActiveError(inst.getId())) {
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
        DeviceSnapshot lineSnapshot = findSnapshotByDeviceName(instanceId, getLineDeviceName(instanceId));
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
        return Optional.ofNullable(instancesById.get(instanceId))
                .map(inst -> inst.getDevices().getLine())
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
     * если displayName не задан в конфиге, отдаем id, а не null.
     */
    private @NonNull String resolveUnitName(PrintSrvProperties.InstanceProperties inst) {
        String displayName = inst.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            return inst.getId();
        }
        return displayName;
    }
}
