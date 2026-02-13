package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.DeviceSnapshotWriter;
import dev.savushkin.scada.mobile.backend.application.ports.PendingWriteCommandsDrainPort;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.WriteCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Scan Cycle планировщик для SCADA системы.
 * <p>
 * Теперь работает с domain моделями вместо DTO:
 * <ul>
 *   <li>{@link DeviceSnapshot} - вместо QueryAllResponseDTO</li>
 *   <li>{@link WriteCommand} - вместо PendingWriteCommand</li>
 * </ul>
 * <p>
 * Реализует классический PLC Scan Cycle паттерн:
 * <ol>
 *   <li>[1] READ из PrintSrv (QueryAll) → DeviceSnapshot</li>
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
    private final PendingWriteCommandsDrainPort pendingCommandsDrain;
    private final DeviceSnapshotWriter snapshotWriter;

    public PrintSrvPollingScheduler(
            PrintSrvConnectionManager connectionManager,
            ScadaCommandExecutor commandExecutor,
            PendingWriteCommandsDrainPort pendingCommandsDrain,
            DeviceSnapshotWriter snapshotWriter,
            @Value("${printsrv.polling.fixed-delay-ms:5000}") long pollingFixedDelayMs
    ) {
        this.connectionManager = connectionManager;
        this.commandExecutor = commandExecutor;
        this.pendingCommandsDrain = pendingCommandsDrain;
        this.snapshotWriter = snapshotWriter;

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
            // [1] READ из PrintSrv - получаем свежие данные как domain модель
            DeviceSnapshot freshData = connectionManager.executeWithRetry(commandExecutor::queryAllSnapshot);

            // [2] BUSINESS LOGIC - получаем pending команды из буфера
            Map<Integer, WriteCommand> pendingWrites = pendingCommandsDrain.drain();

            // [3] WRITE в PrintSrv - если есть команды для записи
            if (!pendingWrites.isEmpty()) {
                try {
                    connectionManager.executeWithRetry(() -> {
                        commandExecutor.executeSetUnitVars(pendingWrites);
                        return null; // void operation
                    });
                } catch (IOException e) {
                    // WRITE failed - команды потеряны
                    // PrintSrv не получил новые значения
                    // Snapshot обновится из READ (старые значения)
                    log.error("❌ [WRITE] failed: {} command(s) lost - {} - {}",
                            pendingWrites.size(), e.getClass().getSimpleName(), e.getMessage());
                    log.warn("Lost commands for units: {}", pendingWrites.keySet());
                }
            }

            // [4] UPDATE snapshot - ВСЕГДА обновляем snapshot из READ
            // Это источник правды: если WRITE не удался, клиенты увидят старые значения
            snapshotWriter.save(freshData);

        } catch (IllegalStateException recoverySkip) {
            // Recovery mode - это ожидаемая ситуация при длительной недоступности PrintSrv
            // Не спамим ERROR, это нормальное поведение
            log.trace("⏸️ Scan cycle skipped: {}", recoverySkip.getMessage());
        } catch (Exception e) {
            // READ failed - PrintSrv недоступен
            // Snapshot НЕ обновляется → клиенты получают stale data
            // Pending команды остаются в буфере до следующего цикла
            log.error("❌ [READ] scan cycle failed (PrintSrv unavailable): {} - {}",
                    e.getClass().getSimpleName(), e.getMessage());
            log.debug("Pending commands remain in buffer (will retry in next cycle)");
        }
    }
}
