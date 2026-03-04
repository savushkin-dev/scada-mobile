package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.ws.ScanCycleCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Единый Scan Cycle планировщик для всех профилей (dev и prod).
 * <p>
 * На каждом цикле опрашивает <b>все</b> инстансы из {@link PrintSrvClientRegistry}
 * и для каждого выполняет {@code QueryAll} по каждому известному устройству.
 * Результаты записываются в {@link InstanceSnapshotRepository} —
 * один snapshot на (instanceId, deviceName).
 * <p>
 * Интервал опроса задаётся в конфигурации:
 * <ul>
 *   <li>dev — {@code printsrv.polling.fixed-delay-ms: 5000} (5 секунд)</li>
 *   <li>prod — {@code printsrv.polling.fixed-delay-ms: 500} (0.5 секунды)</li>
 * </ul>
 * <p>
 * При ошибке опроса одного устройства или инстанса — пропускаем его,
 * остальные продолжают обновляться.
 */
@Service
public class ScanCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScanCycleScheduler.class);

    /**
     * Устройства, опрашиваемые на каждом инстансе PrintSrv.
     * Список одинаков для всех инстансов (согласно документации протокола).
     */
    static final List<String> DEVICES = List.of(
            "Line",
            "scada",
            "BatchQueue",
            "Printer11",
            "CamAgregation",
            "CamAgregationBox",
            "CamChecker"
    );

    private final PrintSrvClientRegistry registry;
    private final PrintSrvMapper mapper;
    private final InstanceSnapshotRepository snapshotRepo;
    private final ApplicationEventPublisher eventPublisher;

    public ScanCycleScheduler(
            PrintSrvClientRegistry registry,
            PrintSrvMapper mapper,
            InstanceSnapshotRepository snapshotRepo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.registry = registry;
        this.mapper = mapper;
        this.snapshotRepo = snapshotRepo;
        this.eventPublisher = eventPublisher;
        log.info("ScanCycleScheduler initialized ({} instances, {} devices per instance)",
                registry.getInstanceIds().size(), DEVICES.size());
    }

    /**
     * Scan cycle: опрашивает все инстансы и все устройства.
     * Период регулируется через {@code printsrv.polling.fixed-delay-ms}.
     */
    @Scheduled(fixedDelayString = "${printsrv.polling.fixed-delay-ms:5000}")
    public void scanCycle() {
        for (String instanceId : registry.getInstanceIds()) {
            PrintSrvClient client = registry.get(instanceId);

            for (String device : DEVICES) {
                try {
                    QueryAllResponseDTO dto = client.queryAll(device);
                    DeviceSnapshot snapshot = mapper.toDomainDeviceSnapshot(dto);
                    snapshotRepo.save(instanceId, device, snapshot);
                } catch (IOException e) {
                    log.trace("Failed to query device '{}' on instance '{}': {}",
                            device, instanceId, e.getMessage());
                }
            }
        }
        // Уведомляем StatusBroadcaster: snapshots обновлены, можно рассылать статус по WS
        eventPublisher.publishEvent(new ScanCycleCompletedEvent(this));
    }
}
