package dev.savushkin.scada.mobile.backend.services.polling;

import dev.savushkin.scada.mobile.backend.client.SocketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Менеджер соединения с PrintSrv и политики восстановления.
 * <p>
 * Ответственность:
 * <ul>
 *   <li>Инвалидировать socket при проблемах связи</li>
 *   <li>Управлять состоянием degraded/recovery</li>
 *   <li>Предоставлять безопасный executeWithRetry(...) для операций, использующих socket</li>
 * </ul>
 * <p>
 * Не знает о конкретных SCADA-командах и DTO. Работает с абстрактным callback.
 */
@Component
public class PrintSrvConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvConnectionManager.class);

    /**
     * Порог последовательных ошибок, после которого запускаем процедуру переподключения.
     */
    private static final int ERROR_THRESHOLD_FOR_RECONNECT = 5;

    private final SocketManager socketManager;

    private final int maxRetryAttempts;
    private final int initialDelayMs;
    private final int maxDelayMs;
    private final long recoveryCheckIntervalMs;

    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicLong lastRecoveryAttemptTime = new AtomicLong(0);

    /**
     * Режим восстановления (degraded mode).
     */
    private volatile boolean inRecoveryMode = false;

    public PrintSrvConnectionManager(
            SocketManager socketManager,
            @Value("${printsrv.retry.max-attempts}") int maxRetryAttempts,
            @Value("${printsrv.retry.initial-delay-ms}") int initialDelayMs,
            @Value("${printsrv.retry.max-delay-ms}") int maxDelayMs,
            @Value("${printsrv.retry.recovery-check-interval-ms}") long recoveryCheckIntervalMs
    ) {
        this.socketManager = Objects.requireNonNull(socketManager, "socketManager");
        this.maxRetryAttempts = maxRetryAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.recoveryCheckIntervalMs = recoveryCheckIntervalMs;

        log.info("PrintSrvConnectionManager initialized (threshold={} failures, maxRetryAttempts={}, recoveryIntervalMs={})",
                ERROR_THRESHOLD_FOR_RECONNECT, maxRetryAttempts, recoveryCheckIntervalMs);
    }

    /**
     * Выполняет операцию, которая требует соединения с PrintSrv, с учётом retry/recovery логики.
     *
     * <ul>
     *   <li>В штатном режиме: выполняет operation один раз</li>
     *   <li>После N последовательных ошибок: запускает reconnection loop с экспоненциальным backoff</li>
     *   <li>После исчерпания попыток: переходит в recovery-mode и пропускает вызовы до момента очередной проверки</li>
     * </ul>
     *
     * @param operation callback, выполняющий реальную socket-операцию
     * @return результат operation
     * @throws Exception пробрасывает последнюю ошибку, если не удалось выполнить operation
     */
    public <T> T executeWithRetry(ThrowingSupplier<T> operation) throws Exception {
        Objects.requireNonNull(operation, "operation");

        if (inRecoveryMode) {
            return executeRecoveryCheck(operation);
        }

        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            long failures = consecutiveFailures.incrementAndGet();
            log.error("❌ PrintSrv operation failed (consecutive failures: {}): {} - {}",
                    failures, e.getClass().getSimpleName(), e.getMessage());

            if (failures >= ERROR_THRESHOLD_FOR_RECONNECT) {
                return executeReconnectionLoop(operation, e);
            }

            throw e;
        }
    }

    private <T> T executeReconnectionLoop(ThrowingSupplier<T> operation, Exception original) throws Exception {
        log.warn("⚠️ ERROR THRESHOLD REACHED ({} failures) - initiating socket reconnection", ERROR_THRESHOLD_FOR_RECONNECT);
        socketManager.invalidate();

        Exception lastException = original;
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                int delay = calculateExponentialDelay(attempt);

                if (attempt > 1) {
                    log.info("⏳ Waiting {}ms before retry attempt {}/{}...", delay, attempt, maxRetryAttempts);
                    sleepInterruptibly(delay);
                }

                log.info("🔌 Reconnection attempt {}/{} to PrintSrv...", attempt, maxRetryAttempts);
                T result = operation.get();

                consecutiveFailures.set(0);
                log.info("✅ Reconnection successful on attempt {}/{}", attempt, maxRetryAttempts);
                return result;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("❌ Reconnection interrupted");
                enterRecoveryMode();
                throw ie;
            } catch (Exception e) {
                lastException = e;
                log.error("❌ Reconnection attempt {}/{} failed: {} - {}",
                        attempt, maxRetryAttempts, e.getClass().getSimpleName(), e.getMessage());
                socketManager.invalidate();
            }
        }

        enterRecoveryMode();
        throw lastException;
    }

    private <T> T executeRecoveryCheck(ThrowingSupplier<T> operation) throws Exception {
        long currentTime = System.currentTimeMillis();
        long lastAttempt = lastRecoveryAttemptTime.get();
        long timeSinceLastAttempt = currentTime - lastAttempt;

        if (lastAttempt != 0 && timeSinceLastAttempt < recoveryCheckIntervalMs) {
            log.trace("Recovery mode: skipping operation ({}ms remaining until next check)",
                    recoveryCheckIntervalMs - timeSinceLastAttempt);
            throw new IllegalStateException("PrintSrv is in recovery mode");
        }

        lastRecoveryAttemptTime.set(currentTime);
        log.info("🔍 Recovery mode: checking PrintSrv availability...");

        try {
            socketManager.invalidate();
            T result = operation.get();

            consecutiveFailures.set(0);
            inRecoveryMode = false;
            log.info("✅ PrintSrv is AVAILABLE again - exiting recovery mode");
            return result;

        } catch (Exception e) {
            log.error("❌ Recovery check failed: PrintSrv still unavailable - {} (next check in {}s)",
                    e.getMessage(), recoveryCheckIntervalMs / 1000);
            socketManager.invalidate();
            throw e;
        }
    }

    private void onSuccess() {
        long previousFailures = consecutiveFailures.getAndSet(0);
        if (previousFailures > 0) {
            log.info("✅ PrintSrv connection recovered after {} consecutive failures", previousFailures);
        }
    }

    private void enterRecoveryMode() {
        inRecoveryMode = true;
        lastRecoveryAttemptTime.set(System.currentTimeMillis());
        log.error("🚨 ENTERING RECOVERY MODE - all reconnection attempts failed. Will check PrintSrv availability every {} seconds.",
                recoveryCheckIntervalMs / 1000);
    }

    private int calculateExponentialDelay(int attempt) {
        long delay = (long) (initialDelayMs * Math.pow(2, attempt - 1));
        return (int) Math.min(delay, maxDelayMs);
    }

    private void sleepInterruptibly(long ms) throws InterruptedException {
        if (ms <= 0) return;
        Thread.sleep(ms);
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

