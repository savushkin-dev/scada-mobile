package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.client.QueryAllCommand;
import dev.savushkin.scada.mobile.backend.client.SocketManager;
import dev.savushkin.scada.mobile.backend.dto.QueryAllRequestDTO;
import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.store.PrintSrvSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис автоматической синхронизации состояния PrintSrv с продвинутой retry-логикой.
 * <p>
 * Выполняет периодический опрос PrintSrv через команду QueryAll
 * и сохраняет результат в in-memory хранилище (PrintSrvSnapshotStore).
 * <p>
 * <b>Частота опроса:</b> каждые 500ms после завершения предыдущего запроса.
 * <p>
 * <b>Стратегия обработки ошибок (трехуровневая):</b>
 * <ol>
 *   <li><b>Штатный режим:</b> При единичных ошибках - логирование и ожидание следующего цикла (500ms)</li>
 *   <li><b>Режим переподключения:</b> После 5 последовательных ошибок - инвалидация socket и повторные
 *       попытки подключения (5 попыток с экспоненциальным backoff: 100ms, 200ms, 400ms, 800ms, 1600ms)</li>
 *   <li><b>Режим восстановления:</b> После исчерпания попыток - переход в degraded mode с проверкой
 *       доступности PrintSrv каждые 60 секунд</li>
 * </ol>
 * <p>
 * <b>Гарантии для клиентов:</b>
 * <ul>
 *   <li>REST API продолжает работать с последним валидным snapshot</li>
 *   <li>Клиенты не видят ошибок соединения</li>
 *   <li>Автоматическое восстановление при появлении PrintSrv</li>
 * </ul>
 */
@Service
public class ScadaDataPollingService {

    private static final Logger log = LoggerFactory.getLogger(ScadaDataPollingService.class);

    // Пороги для переключения режимов
    private static final int ERROR_THRESHOLD_FOR_RECONNECT = 5;

    private final QueryAllCommand queryAllCommand;
    private final PrintSrvSnapshotStore snapshotStore;
    private final SocketManager socketManager;

    // Конфигурация из application.yaml
    private final int maxRetryAttempts;
    private final int initialDelayMs;
    private final int maxDelayMs;
    private final long recoveryCheckIntervalMs;

    // Счетчики состояния
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastRecoveryAttemptTime = new AtomicLong(0);

    // Флаг режима восстановления
    private volatile boolean inRecoveryMode = false;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param queryAllCommand         команда для опроса PrintSrv
     * @param snapshotStore           хранилище для сохранения snapshot
     * @param socketManager           менеджер socket-соединения для управления переподключением
     * @param maxRetryAttempts        максимальное количество попыток переподключения
     * @param initialDelayMs          начальная задержка для экспоненциального backoff
     * @param maxDelayMs              максимальная задержка между попытками
     * @param recoveryCheckIntervalMs интервал проверки в режиме восстановления
     */
    public ScadaDataPollingService(
            QueryAllCommand queryAllCommand,
            PrintSrvSnapshotStore snapshotStore,
            SocketManager socketManager,
            @Value("${printsrv.retry.max-attempts}") int maxRetryAttempts,
            @Value("${printsrv.retry.initial-delay-ms}") int initialDelayMs,
            @Value("${printsrv.retry.max-delay-ms}") int maxDelayMs,
            @Value("${printsrv.retry.recovery-check-interval-ms}") long recoveryCheckIntervalMs
    ) {
        this.queryAllCommand = queryAllCommand;
        this.snapshotStore = snapshotStore;
        this.socketManager = socketManager;
        this.maxRetryAttempts = maxRetryAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.recoveryCheckIntervalMs = recoveryCheckIntervalMs;
        log.info("ScadaDataPollingService initialized - polling interval: 500ms, " +
                        "reconnect threshold: {} errors, max retry attempts: {}, recovery interval: {}ms",
                ERROR_THRESHOLD_FOR_RECONNECT, maxRetryAttempts, recoveryCheckIntervalMs);
    }

    /**
     * Опрашивает состояние PrintSrv и обновляет snapshot в хранилище.
     * <p>
     * Метод выполняется автоматически по расписанию (каждые 500ms после
     * завершения предыдущего выполнения). Работает автономно и не зависит
     * от REST запросов клиентов.
     * <p>
     * <b>Режимы работы:</b>
     * <ul>
     *   <li><b>Штатный:</b> Опрос каждые 500ms, при единичных ошибках - логирование</li>
     *   <li><b>Переподключение:</b> После 5 ошибок - инвалидация socket + retry с backoff</li>
     *   <li><b>Восстановление:</b> После исчерпания попыток - проверка каждые 60 секунд</li>
     * </ul>
     * <p>
     * Клиенты продолжают получать последний валидный snapshot независимо от режима.
     */
    @Scheduled(fixedDelay = 500)
    public void pollPrintSrvState() {
        // Проверка режима восстановления
        if (inRecoveryMode) {
            handleRecoveryMode();
            return;
        }

        // Штатный режим или режим переподключения
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

            // Успешное выполнение - сброс счетчика ошибок
            handleSuccessfulPoll();

        } catch (Exception e) {
            // Обработка ошибки с учетом текущего состояния
            handlePollingError(e);
        }
    }

    /**
     * Обрабатывает успешный опрос PrintSrv.
     * <p>
     * Сбрасывает счетчик ошибок и логирует восстановление соединения (если было).
     */
    private void handleSuccessfulPoll() {
        int previousFailures = consecutiveFailures.getAndSet(0);

        if (previousFailures > 0) {
            log.info("✅ PrintSrv connection recovered after {} consecutive failures", previousFailures);
        }

        // Выход из режима восстановления
        if (inRecoveryMode) {
            inRecoveryMode = false;
            log.info("✅ Exited recovery mode - normal polling resumed");
        }
    }

    /**
     * Обрабатывает ошибку опроса PrintSrv.
     * <p>
     * Стратегия:
     * <ol>
     *   <li>Инкремент счетчика последовательных ошибок</li>
     *   <li>Если ошибок < 5: логирование и ожидание следующего цикла</li>
     *   <li>Если ошибок >= 5: переход в режим переподключения</li>
     * </ol>
     *
     * @param e исключение, возникшее при опросе
     */
    private void handlePollingError(Exception e) {
        int failures = consecutiveFailures.incrementAndGet();

        log.error("❌ Failed to poll PrintSrv (consecutive failures: {}): {} - {}",
                failures, e.getClass().getSimpleName(), e.getMessage());

        // Порог для переподключения
        if (failures >= ERROR_THRESHOLD_FOR_RECONNECT) {
            log.warn("⚠️ ERROR THRESHOLD REACHED ({} failures) - initiating socket reconnection",
                    ERROR_THRESHOLD_FOR_RECONNECT);
            handleReconnection();
        } else {
            log.debug("Waiting for next polling cycle (in 500ms)...");
        }
    }

    /**
     * Обрабатывает переподключение к PrintSrv с retry-логикой.
     * <p>
     * Стратегия:
     * <ol>
     *   <li>Инвалидация текущего socket</li>
     *   <li>5 попыток подключения с экспоненциальным backoff (100ms, 200ms, 400ms, 800ms, 1600ms)</li>
     *   <li>При успехе: возврат в штатный режим</li>
     *   <li>При неудаче: переход в режим восстановления (проверка каждые 60 секунд)</li>
     * </ol>
     */
    private void handleReconnection() {
        log.info("🔄 Starting reconnection procedure...");

        // Инвалидируем текущий socket
        socketManager.invalidate();

        // Пытаемся переподключиться с экспоненциальным backoff
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                int delay = calculateExponentialDelay(attempt);

                if (attempt > 1) {
                    log.info("⏳ Waiting {}ms before retry attempt {}/{}...", delay, attempt, maxRetryAttempts);
                    Thread.sleep(delay);
                }

                log.info("🔌 Reconnection attempt {}/{} to PrintSrv...", attempt, maxRetryAttempts);

                // Пытаемся выполнить запрос (это автоматически создаст новый socket)
                QueryAllRequestDTO request = new QueryAllRequestDTO("Line", "QueryAll");
                QueryAllResponseDTO snapshot = queryAllCommand.execute(request);

                // Успех! Сохраняем snapshot и сбрасываем счетчики
                snapshotStore.saveSnapshot(snapshot);
                consecutiveFailures.set(0);

                log.info("✅ Reconnection successful on attempt {}/{}", attempt, maxRetryAttempts);
                return;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("❌ Reconnection interrupted");
                enterRecoveryMode();
                return;
            } catch (Exception e) {
                log.error("❌ Reconnection attempt {}/{} failed: {} - {}",
                        attempt, maxRetryAttempts, e.getClass().getSimpleName(), e.getMessage());

                // Инвалидируем socket перед следующей попыткой
                socketManager.invalidate();
            }
        }

        // Все попытки исчерпаны - переход в режим восстановления
        enterRecoveryMode();
    }

    /**
     * Переводит сервис в режим восстановления.
     * <p>
     * В этом режиме попытки подключения выполняются раз в минуту для снижения нагрузки.
     */
    private void enterRecoveryMode() {
        inRecoveryMode = true;
        lastRecoveryAttemptTime.set(System.currentTimeMillis());
        log.error("🚨 ENTERING RECOVERY MODE - all reconnection attempts failed. " +
                        "Will check PrintSrv availability every {} seconds. Clients continue to use last valid snapshot.",
                recoveryCheckIntervalMs / 1000);
    }

    /**
     * Обрабатывает режим восстановления.
     * <p>
     * Выполняет проверку доступности PrintSrv каждые 60 секунд.
     * При успешном подключении - возврат в штатный режим.
     */
    private void handleRecoveryMode() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastAttempt = currentTime - lastRecoveryAttemptTime.get();

        // Проверяем, прошло ли достаточно времени с последней попытки
        if (timeSinceLastAttempt < recoveryCheckIntervalMs) {
            log.trace("Recovery mode: waiting for next check ({}ms remaining)",
                    recoveryCheckIntervalMs - timeSinceLastAttempt);
            return;
        }

        // Обновляем время последней попытки
        lastRecoveryAttemptTime.set(currentTime);

        log.info("🔍 Recovery mode: checking PrintSrv availability...");

        try {
            // Инвалидируем старый socket и пытаемся подключиться
            socketManager.invalidate();

            QueryAllRequestDTO request = new QueryAllRequestDTO("Line", "QueryAll");
            QueryAllResponseDTO snapshot = queryAllCommand.execute(request);

            // Успех! Выходим из режима восстановления
            snapshotStore.saveSnapshot(snapshot);
            consecutiveFailures.set(0);
            inRecoveryMode = false;

            log.info("✅ PrintSrv is AVAILABLE again - exiting recovery mode, resuming normal polling");

        } catch (Exception e) {
            log.error("❌ Recovery check failed: PrintSrv still unavailable - {} (next check in {}s)",
                    e.getMessage(), recoveryCheckIntervalMs / 1000);
            socketManager.invalidate();
        }
    }

    /**
     * Вычисляет задержку для экспоненциального backoff.
     * <p>
     * Формула: min(initialDelay * 2^(attempt-1), maxDelay)
     *
     * @param attempt номер попытки (1-based)
     * @return задержка в миллисекундах
     */
    private int calculateExponentialDelay(int attempt) {
        long delay = (long) (initialDelayMs * Math.pow(2, attempt - 1));
        return (int) Math.min(delay, maxDelayMs);
    }
}
