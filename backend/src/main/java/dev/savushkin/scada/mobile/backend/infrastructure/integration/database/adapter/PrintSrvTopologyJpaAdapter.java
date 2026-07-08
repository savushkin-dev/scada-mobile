package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.Workshop;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.WorkshopJpaRepository;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * JPA-адаптер порта {@link PrintSrvTopologyRepository}.
 * <p>
 * Читает топологию PrintSrv из БД: цеха, аппараты, устройства, хосты/порты.
 * Системные устройства (Line, scada, BatchQueue) захардкожены — они одинаковы
 * для всех инстансов и не хранятся в {@code unit_devices}.
 */
@Component
public class PrintSrvTopologyJpaAdapter implements PrintSrvTopologyRepository {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvTopologyJpaAdapter.class);

    private static final String TYPE_PRINTER = "printer";
    private static final String TYPE_AGGREGATION_CAM = "aggregation_cam";
    private static final String TYPE_AGGREGATION_BOX_CAM = "aggregation_box_cam";
    private static final String TYPE_CHECKER_CAM = "checker_cam";

    private final UnitJpaRepository unitRepository;
    private final WorkshopJpaRepository workshopRepository;
    private final DeviceJpaRepository deviceRepository;

    private volatile String cachedETag = null;

    public PrintSrvTopologyJpaAdapter(
            UnitJpaRepository unitRepository,
            WorkshopJpaRepository workshopRepository,
            DeviceJpaRepository deviceRepository
    ) {
        this.unitRepository = unitRepository;
        this.workshopRepository = workshopRepository;
        this.deviceRepository = deviceRepository;
    }

    @Override
    public @NonNull List<PrintSrvInstance> findAllActiveInstances() {
        List<UnitEntity> units = unitRepository.findByActiveTrueAndPrintsrvInstanceIdIsNotNull();
        List<PrintSrvInstance> result = new ArrayList<>(units.size());

        for (UnitEntity unit : units) {
            PrintSrvInstance inst = buildInstance(unit);
            if (inst != null) {
                result.add(inst);
            }
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public @NonNull Optional<PrintSrvInstance> findByInstanceId(@NonNull String instanceId) {
        return unitRepository.findByPrintsrvInstanceId(instanceId)
                .filter(UnitEntity::isActive)
                .map(this::buildInstance);
    }

    @Override
    public @NonNull List<Workshop> findAllActiveWorkshops() {
        return workshopRepository.findByActiveTrue().stream()
                .map(w -> new Workshop(w.getId(), w.getName()))
                .toList();
    }

    @Override
    public @NonNull String getConfigETag() {
        if (cachedETag == null) {
            cachedETag = computeETag();
        }
        return cachedETag;
    }

    /**
     * Инвалидирует кэш ETag. Вызывается при изменении данных через admin-контроллер.
     */
    public void invalidateETag() {
        cachedETag = null;
        log.debug("ETag cache invalidated");
    }

    // ─── Private helpers ───────────────────────────────────────────────────

    private PrintSrvInstance buildInstance(UnitEntity unit) {
        String instanceId = unit.getPrintsrvInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            return null;
        }

        long workshopId = unit.getWorkshop() != null ? unit.getWorkshop().getId() : 0L;

        String host = unit.getPrintsrvHost() != null ? unit.getPrintsrvHost() : "";
        int port = unit.getPrintsrvPort() != null ? unit.getPrintsrvPort() : 0;

        List<DeviceEntity> devices = deviceRepository.findByUnit_PrintsrvInstanceId(instanceId);

        List<String> printers = new ArrayList<>();
        List<String> aggregationCams = new ArrayList<>();
        List<String> aggregationBoxCams = new ArrayList<>();
        List<String> checkerCams = new ArrayList<>();

        for (DeviceEntity device : devices) {
            DeviceCatalogEntity catalog = device.getCatalog();
            if (catalog == null || !catalog.isActive() || catalog.getType() == null) {
                continue; // Пропускаем неактивные и неполностью сконфигурированные
            }
            String typeCode = catalog.getType().getCode();
            String code = catalog.getCode();
            if (typeCode == null || code == null) {
                continue;
            }
            switch (typeCode) {
                case TYPE_PRINTER -> printers.add(code);
                case TYPE_AGGREGATION_CAM -> aggregationCams.add(code);
                case TYPE_AGGREGATION_BOX_CAM -> aggregationBoxCams.add(code);
                case TYPE_CHECKER_CAM -> checkerCams.add(code);
                default -> log.debug("Unknown device type '{}' for instance '{}'", typeCode, instanceId);
            }
        }

        // Системные устройства — захардкожены, одинаковы для всех инстансов
        List<String> allDeviceNames = new ArrayList<>();
        allDeviceNames.add("Line");
        allDeviceNames.add("scada");
        allDeviceNames.add("BatchQueue");
        allDeviceNames.addAll(printers);
        allDeviceNames.addAll(aggregationCams);
        allDeviceNames.addAll(aggregationBoxCams);
        allDeviceNames.addAll(checkerCams);

        return new PrintSrvInstance(
                instanceId,
                unit.getName(),
                workshopId,
                host,
                port,
                Collections.unmodifiableList(allDeviceNames),
                Collections.unmodifiableList(printers),
                Collections.unmodifiableList(aggregationCams),
                Collections.unmodifiableList(aggregationBoxCams),
                Collections.unmodifiableList(checkerCams)
        );
    }

    private @NonNull String computeETag() {
        try {
            StringBuilder sb = new StringBuilder();

            List<Workshop> workshops = findAllActiveWorkshops();
            workshops.stream()
                    .sorted(Comparator.comparing(Workshop::id))
                    .forEach(ws -> sb.append("w:").append(ws.id()).append(':').append(ws.displayName()).append(';'));

            List<PrintSrvInstance> instances = findAllActiveInstances();
            instances.stream()
                    .sorted(Comparator.comparing(PrintSrvInstance::instanceId))
                    .forEach(inst -> sb.append("i:").append(inst.instanceId()).append(':')
                            .append(inst.workshopId()).append(':').append(inst.displayName()).append(':')
                            .append(String.join(",", inst.deviceNames())).append(';'));

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
