package dev.savushkin.scada.mobile.backend.application;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Сервис приложения для координации health-check логики SCADA.
 * <p>
 * Данные о состоянии аппаратов предоставляются через {@link InstanceSnapshotRepository},
 * который автоматически заполняется планировщиком опроса {@code ScanCycleScheduler}.
 */
@Service
public class ScadaApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ScadaApplicationService.class);

    private final InstanceSnapshotRepository snapshotRepository;

    public ScadaApplicationService(InstanceSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
        log.debug("ScadaApplicationService initialized");
    }

    /**
     * Проверяет, готова ли система обслуживать запросы.
     * <p>
     * Система готова, когда получен хотя бы один snapshot
     * от цикла опроса PrintSrv.
     *
     * @return true, если система готова
     */
    public boolean isReady() {
        return snapshotRepository.hasAnySnapshot();
    }

    /**
     * Проверяет, живо ли приложение (liveness probe).
     *
     * @return true, если приложение живо
     */
    public boolean isAlive() {
        return true;
    }
}
