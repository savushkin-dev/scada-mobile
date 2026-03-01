package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.DeviceSnapshotWriter;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Dev-профильный аналог {@link PrintSrvPollingScheduler}.
 * <p>
 * Вместо реальных TCP-соединений использует {@link PrintSrvClientRegistry},
 * который в dev-профиле разрешается в {@code MockPrintSrvClientRegistry}.
 * <p>
 * Реализует тот же паттерн Scan Cycle:
 * <ol>
 *   <li>Для каждого онлайн-инстанса выполняет {@code queryAll("Line")}</li>
 *   <li>Преобразует ответ в domain {@link DeviceSnapshot}</li>
 *   <li>Записывает первый валидный snapshot в {@link PrintSrvSnapshotStore}
 *       (для совместимости с {@code ScadaApplicationService.isReady()})</li>
 * </ol>
 * <p>
 * Этот бин активен <b>только</b> при {@code spring.profiles.active=dev}.
 * В prod-профиле его место занимает {@link PrintSrvPollingScheduler}.
 */
@Service
@Profile("dev")
public class DevScanCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(DevScanCycleScheduler.class);

    /** Устройство, используемое как источник «главного» snapshot для store. */
    private static final String PRIMARY_DEVICE = "Line";

    private final PrintSrvClientRegistry registry;
    private final PrintSrvMapper mapper;
    private final DeviceSnapshotWriter snapshotWriter;

    public DevScanCycleScheduler(
            PrintSrvClientRegistry registry,
            PrintSrvMapper mapper,
            DeviceSnapshotWriter snapshotWriter
    ) {
        this.registry = registry;
        this.mapper = mapper;
        this.snapshotWriter = snapshotWriter;
        log.info("DevScanCycleScheduler initialized ({} instances registered)", registry.getInstanceIds().size());
    }

    /**
     * Scan cycle: опрашивает mock-инстансы и актуализирует snapshot store.
     * <p>
     * Период регулируется через {@code printsrv.polling.fixed-delay-ms} (default 5000 мс).
     * Сохраняет snapshot первого онлайн-инстанса — достаточно для {@code isReady()=true}.
     */
    @Scheduled(fixedDelayString = "${printsrv.polling.fixed-delay-ms:5000}")
    public void scanCycle() {
        boolean saved = false;

        for (String instanceId : registry.getInstanceIds()) {
            PrintSrvClient client = registry.get(instanceId);

            if (!client.isAlive()) {
                log.trace("Instance {} is offline — skipping", instanceId);
                continue;
            }

            try {
                QueryAllResponseDTO dto = client.queryAll(PRIMARY_DEVICE);
                DeviceSnapshot snapshot = mapper.toDomainDeviceSnapshot(dto);
                snapshotWriter.save(snapshot);
                saved = true;
                log.debug("Snapshot saved from instance '{}' device '{}' ({} units)",
                        instanceId, PRIMARY_DEVICE, snapshot.getUnitCount());
                break; // Один snapshot достаточен для isReady()
            } catch (IOException e) {
                log.warn("Failed to query instance '{}': {}", instanceId, e.getMessage());
            }
        }

        if (!saved) {
            log.warn("All instances are offline — snapshot store not updated this cycle");
        }
    }
}
