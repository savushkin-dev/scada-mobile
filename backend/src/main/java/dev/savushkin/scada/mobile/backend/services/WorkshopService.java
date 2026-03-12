package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.*;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
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
                           DeviceCompositionService deviceCompositionService) {
        this.config = config;
        this.snapshotRepo = snapshotRepo;
        this.deviceCompositionService = deviceCompositionService;
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
                .map(inst -> new UnitTopologyDTO(inst.getId(), inst.getWorkshopId(), inst.getDisplayName()))
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
                inst.getDisplayName(),
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
     * Проверяет флаг Error на устройстве Line.
     */
    private boolean hasActiveError(String instanceId) {
        DeviceSnapshot lineSnapshot = snapshotRepo.get(instanceId, getLineDeviceName(instanceId));
        if (lineSnapshot == null) {
            return false;
        }
        for (UnitSnapshot unit : lineSnapshot.units().values()) {
            Optional<String> error = unit.properties().getError();
            if (error.isPresent() && !"0".equals(error.get()) && !error.get().isEmpty()) {
                return true;
            }
        }
        return false;
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
     */
    private @NonNull String deriveEvent(String instanceId) {
        DeviceSnapshot lineSnapshot = snapshotRepo.get(instanceId, getLineDeviceName(instanceId));
        if (lineSnapshot == null) {
            return "Нет данных";
        }
        for (UnitSnapshot unit : lineSnapshot.units().values()) {
            Optional<String> st = unit.properties().getSt();
            Optional<String> error = unit.properties().getError();
            Optional<String> errorMsg = unit.properties().getErrorMessage();

            // 1. Line.Error — основной флаг ошибки линии
            boolean hasError = error.isPresent() && !"0".equals(error.get()) && !error.get().isEmpty();
            if (hasError) {
                return errorMsg.filter(m -> !m.isEmpty()).orElse("Ошибка");
            }

            // 2. scada.lineerr — дополнительный источник ошибки (из scada-снапшота)
            String lineerr = getScadaLineerr(instanceId);
            if (lineerr != null && !"0".equals(lineerr) && !lineerr.isBlank()) {
                return lineerr;
            }

            boolean isRunning = st.isPresent() && "1".equals(st.get());
            return isRunning ? "В работе" : "Остановлена";
        }
        return "Нет данных";
    }

    /**
     * Читает значение поля {@code lineerr} из scada-снапшота данного инстанса.
     * Возвращает {@code null}, если снапшот недоступен или поле отсутствует.
     */
    private @Nullable String getScadaLineerr(@NonNull String instanceId) {
        String scadaName = Optional.ofNullable(instancesById.get(instanceId))
                .map(inst -> inst.getDevices().getScada())
                .orElse("scada");
        DeviceSnapshot scadaSnap = snapshotRepo.get(instanceId, scadaName);
        if (scadaSnap == null || scadaSnap.units().isEmpty()) {
            return null;
        }
        return scadaSnap.units().values().iterator().next()
                .properties().getRawProperties().get("lineerr");
    }

    private @NonNull String getLineDeviceName(@NonNull String instanceId) {
        return Optional.ofNullable(instancesById.get(instanceId))
                .map(inst -> inst.getDevices().getLine())
                .orElse("Line");
    }
}
