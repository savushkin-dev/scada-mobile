package dev.savushkin.scada.mobile.backend.services.polling;

import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Планировщик polling'а PrintSrv.
 * <p>
 * Ответственность: только расписание и оркестрация.
 * Вся логика соединения/повторов вынесена в {@link PrintSrvConnectionManager},
 * а выполнение команд — в {@link ScadaCommandExecutor}.
 */
@Service
public class PrintSrvPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvPollingScheduler.class);

    private final PrintSrvConnectionManager connectionManager;
    private final ScadaCommandExecutor commandExecutor;
    private final PrintSrvSnapshotStore snapshotStore;

    public PrintSrvPollingScheduler(
            PrintSrvConnectionManager connectionManager,
            ScadaCommandExecutor commandExecutor,
            PrintSrvSnapshotStore snapshotStore,
            @Value("${printsrv.polling.fixed-delay-ms:500}") long pollingFixedDelayMs
    ) {
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");

        log.info("PrintSrvPollingScheduler initialized - polling fixedDelay: {}ms", pollingFixedDelayMs);
    }

    @Scheduled(fixedDelayString = "${printsrv.polling.fixed-delay-ms:500}")
    public void pollPrintSrvState() {
        try {
            log.trace("Starting PrintSrv polling cycle");

            QueryAllResponseDTO snapshot = connectionManager.executeWithRetry(commandExecutor::queryAllSnapshot);
            snapshotStore.saveSnapshot(snapshot);

            log.trace("Snapshot saved to store successfully");
        } catch (IllegalStateException recoverySkip) {
            // Фоновый сервис в recovery-mode — это ожидаемо. Не спамим ERROR.
            log.trace("Polling skipped: {}", recoverySkip.getMessage());
        } catch (Exception e) {
            // Ошибка уже учтена в connectionManager (счетчики/режимы),
            // здесь оставим минимальный лог чтобы видеть контуры цикла.
            log.debug("Polling cycle ended with exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
