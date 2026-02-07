package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.client.QueryAllCommand;
import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Сервис автоматической синхронизации состояния PrintSrv.
 * <p>
 * Выполняет периодический опрос PrintSrv через команду QueryAll
 * и сохраняет результат в in-memory хранилище (PrintSrvSnapshotStore).
 * <p>
 * Частота опроса: каждые 500ms после завершения предыдущего запроса.
 * При ошибках опрос продолжается, сохраняя предыдущий snapshot доступным.
 */
@Service
public class ScadaDataPollingService {

    private static final Logger log = LoggerFactory.getLogger(ScadaDataPollingService.class);

    private final QueryAllCommand queryAllCommand;
    private final PrintSrvSnapshotStore snapshotStore;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param queryAllCommand команда для опроса PrintSrv
     * @param snapshotStore   хранилище для сохранения snapshot
     */
    public ScadaDataPollingService(
            QueryAllCommand queryAllCommand,
            PrintSrvSnapshotStore snapshotStore
    ) {
        this.queryAllCommand = queryAllCommand;
        this.snapshotStore = snapshotStore;
        log.info("ScadaDataPollingService initialized - will poll PrintSrv every 500ms");
    }

    /**
     * Опрашивает состояние PrintSrv и обновляет snapshot в хранилище.
     * <p>
     * Метод выполняется автоматически по расписанию (каждые 500ms после
     * завершения предыдущего выполнения). Работает автономно и не зависит
     * от REST запросов клиентов.
     * <p>
     * При ошибках:
     * <ul>
     *   <li>Логируется полная информация об ошибке</li>
     *   <li>Выполнение не прерывается</li>
     *   <li>Следующий опрос произойдет по расписанию</li>
     *   <li>Предыдущий snapshot остается доступным для клиентов</li>
     * </ul>
     */
    @Scheduled(fixedDelay = 500)
    public void pollPrintSrvState() {
        try {
            log.trace("Starting PrintSrv polling cycle");

            // Формируем запрос QueryAll
            QueryAllRequestDTO request = new QueryAllRequestDTO("Line", "QueryAll");
            log.debug("Executing QueryAll command to PrintSrv");

            // Выполняем запрос через socket
            QueryAllResponseDTO snapshot = queryAllCommand.execute(request);
            log.debug("Received snapshot from PrintSrv with {} units", snapshot.units().size());

            // Сохраняем snapshot в store (thread-safe)
            snapshotStore.saveSnapshot(snapshot);
            log.trace("Snapshot saved to store successfully");

        } catch (Exception e) {
            // Логируем ошибку с полным stack trace
            log.error("Failed to poll PrintSrv state: {}", e.getMessage(), e);
            // Не падаем - продолжаем опрос по расписанию
            // Старый snapshot остаётся доступным для клиентов
        }
    }
}
