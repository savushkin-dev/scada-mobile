package dev.savushkin.scada.mobile.backend.services.polling;

import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.store.PendingCommandsBuffer;
import dev.savushkin.scada.mobile.backend.store.PendingWriteCommand;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Scan Cycle планировщик для PrintSrv.
 * <p>
 * Реализует классический PLC Scan Cycle паттерн:
 * <ol>
 *   <li>[1] READ из PrintSrv (QueryAll)</li>
 *   <li>[2] BUSINESS LOGIC (слияние с pending командами)</li>
 *   <li>[3] WRITE в PrintSrv (SetUnitVars если есть команды)</li>
 *   <li>[4] UPDATE snapshot (независимо от успеха записи)</li>
 * </ol>
 * <p>
 * Архитектурные гарантии:
 * <ul>
 *   <li><b>Один поток</b>: последовательное выполнение исключает race conditions</li>
 *   <li><b>Eventual Consistency</b>: snapshot актуализируется каждые 5 секунд</li>
 *   <li><b>Last-Write-Wins</b>: при конфликтах команд сохраняется последняя</li>
 *   <li><b>Graceful Degradation</b>: при ошибках PrintSrv команды накапливаются в буфере</li>
 * </ul>
 */
@Service
public class PrintSrvPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvPollingScheduler.class);

    private final PrintSrvConnectionManager connectionManager;
    private final ScadaCommandExecutor commandExecutor;
    private final PendingCommandsBuffer pendingCommandsBuffer;
    private final PrintSrvSnapshotStore snapshotStore;

    public PrintSrvPollingScheduler(
            PrintSrvConnectionManager connectionManager,
            ScadaCommandExecutor commandExecutor,
            PendingCommandsBuffer pendingCommandsBuffer,
            PrintSrvSnapshotStore snapshotStore,
            @Value("${printsrv.polling.fixed-delay-ms:5000}") long pollingFixedDelayMs
    ) {
        this.connectionManager = connectionManager;
        this.commandExecutor = commandExecutor;
        this.pendingCommandsBuffer = pendingCommandsBuffer;
        this.snapshotStore = snapshotStore;

        log.info("PrintSrvPollingScheduler initialized - scan cycle interval: {}ms", pollingFixedDelayMs);
    }

    /**
     * Scan Cycle: выполняется каждые 5 секунд (настраивается через printsrv.polling.fixed-delay-ms).
     * <p>
     * Последовательно выполняет READ → BUSINESS LOGIC → WRITE → UPDATE snapshot.
     */
    @Scheduled(fixedDelayString = "${printsrv.polling.fixed-delay-ms:5000}")
    public void scanCycle() {
        try {
            log.trace("🔄 Starting scan cycle");

            // [1] READ из PrintSrv - получаем свежие данные
            QueryAllResponseDTO freshData = connectionManager.executeWithRetry(commandExecutor::queryAllSnapshot);
            log.trace("✅ [1/4] READ completed: {} units received", freshData.units().size());

            // [2] BUSINESS LOGIC - получаем pending команды из буфера
            Map<Integer, PendingWriteCommand> pendingWrites = pendingCommandsBuffer.getAndClear();
            log.trace("📋 [2/4] BUSINESS LOGIC: {} pending command(s) retrieved", pendingWrites.size());

            // [3] WRITE в PrintSrv - если есть команды для записи
            if (!pendingWrites.isEmpty()) {
                try {
                    connectionManager.executeWithRetry(() -> {
                        commandExecutor.executeSetUnitVars(pendingWrites);
                        return null; // void operation
                    });
                    log.debug("✅ [3/4] WRITE completed: {} command(s) written to PrintSrv",
                            pendingWrites.size());
                } catch (IOException e) {
                    // WRITE failed - команды потеряны
                    // PrintSrv не получил новые значения
                    // Snapshot обновится из READ (старые значения)
                    log.error("❌ [3/4] WRITE failed: {} command(s) lost - {} - {}",
                            pendingWrites.size(), e.getClass().getSimpleName(), e.getMessage());
                    log.warn("Lost commands for units: {}", pendingWrites.keySet());
                }
            } else {
                log.trace("⏭️ [3/4] WRITE skipped: no pending commands");
            }

            // [4] UPDATE snapshot - ВСЕГДА обновляем snapshot из READ
            // Это источник правды: если WRITE не удался, клиенты увидят старые значения
            snapshotStore.saveSnapshot(freshData);
            log.trace("✅ [4/4] UPDATE snapshot completed");

            log.debug("🔄 Scan cycle completed successfully");

        } catch (IllegalStateException recoverySkip) {
            // Recovery mode - это ожидаемая ситуация при длительной недоступности PrintSrv
            // Не спамим ERROR, это нормальное поведение
            log.trace("⏸️ Scan cycle skipped: {}", recoverySkip.getMessage());
        } catch (Exception e) {
            // READ failed - PrintSrv недоступен
            // Snapshot НЕ обновляется → клиенты получают stale data
            // Pending команды остаются в буфере до следующего цикла
            log.error("❌ Scan cycle failed (PrintSrv unavailable): {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
            log.debug("Pending commands remain in buffer (size={}), will retry in next cycle",
                    pendingCommandsBuffer.size());
        }
    }
}
