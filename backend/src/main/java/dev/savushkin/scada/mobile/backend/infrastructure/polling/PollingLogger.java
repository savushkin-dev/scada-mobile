package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Dedicated логгер для детального диагностического логирования polling-процесса.
 * <p>
 * Работает через отдельный SLF4J-логгер с именем {@code scada.polling.diagnostic},
 * который настроен в {@code logback-spring.xml} на запись в два собственных файла:
 * <ul>
 *   <li>{@code polling.json} — структурированный JSON для анализа нейросетью</li>
 *   <li>{@code polling.log} — человекочитаемый plain text</li>
 * </ul>
 * <p>
 * Логи изолируются от остальной системы благодаря {@code additivity="false"}.
 * MDC-поля ({@code instanceId}, {@code device}, {@code step}) попадают в JSON автоматически.
 *
 * <h3>Уровни логирования</h3>
 * <ul>
 *   <li>{@code DEBUG} — каждый шаг polling-цикла (соединение, запрос, ответ, парсинг, сохранение)</li>
 *   <li>{@code TRACE} — низкоуровневые детали (raw bytes, полные JSON-ответы)</li>
 *   <li>{@code WARN} — ошибки отдельных устройств (не прерывают цикл)</li>
 *   <li>{@code ERROR} — критические ошибки (невозможно продолжить polling)</li>
 * </ul>
 */
public final class PollingLogger {

    private static final Logger log = LoggerFactory.getLogger("scada.polling.diagnostic");

    private static final String MDC_INSTANCE = "instanceId";
    private static final String MDC_DEVICE = "device";
    private static final String MDC_STEP = "step";
    private static final String MDC_COMPONENT = "component";

    private PollingLogger() {
        // utility class
    }

    // ─── MDC helpers ──────────────────────────────────────────────────────────

    private static void withMdc(@Nullable String instanceId, @Nullable String device, int step, Runnable action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (instanceId != null) {
                MDC.put(MDC_INSTANCE, instanceId);
            }
            if (device != null) {
                MDC.put(MDC_DEVICE, device);
            }
            MDC.put(MDC_STEP, String.valueOf(step));
            MDC.put(MDC_COMPONENT, "POLLING");
            action.run();
        } finally {
            if (previous != null) {
                MDC.setContextMap(previous);
            } else {
                MDC.clear();
            }
        }
    }

    private static void withMdc(@Nullable String instanceId, int step, Runnable action) {
        withMdc(instanceId, null, step, action);
    }

    // ─── Runtime lifecycle ────────────────────────────────────────────────────

    /**
     * STEP 0 — Инициализация polling runtime.
     */
    public static void logRuntimeInitialized(int workerCount, int totalDevices, long delayMs) {
        withMdc(null, 0, () ->
                log.debug("[INIT] PrintSrvPollingRuntime initialized: workers={}, devices={}, delay={}ms",
                        workerCount, totalDevices, delayMs)
        );
    }

    /**
     * STEP 0 — Запуск polling runtime.
     */
    public static void logRuntimeStarted(int workerCount) {
        withMdc(null, 0, () ->
                log.debug("[INIT] PrintSrvPollingRuntime started with {} virtual worker(s)", workerCount)
        );
    }

    /**
     * STEP 0 — Остановка polling runtime.
     */
    public static void logRuntimeStopped() {
        withMdc(null, 0, () ->
                log.debug("[INIT] PrintSrvPollingRuntime stopped")
        );
    }

    /**
     * STEP 0 — Worker запущен.
     */
    public static void logWorkerStarted(@NonNull String instanceId) {
        withMdc(instanceId, 0, () ->
                log.debug("[INIT] polling worker started for instance '{}'", instanceId)
        );
    }

    /**
     * STEP 0 — Worker остановлен.
     */
    public static void logWorkerStopped(@NonNull String instanceId) {
        withMdc(instanceId, 0, () ->
                log.debug("[INIT] polling worker stopped for instance '{}'", instanceId)
        );
    }

    /**
     * STEP 0 — Неожиданная ошибка в worker.
     */
    public static void logWorkerFailure(@NonNull String instanceId, @NonNull Throwable ex) {
        withMdc(instanceId, 0, () ->
                log.error("[INIT] unexpected polling worker failure for instance '{}': {}", instanceId, ex.getMessage(), ex)
        );
    }

    // ─── Poll cycle (per instance) ────────────────────────────────────────────

    /**
     * STEP 1 — Начало poll-цикла для инстанса.
     */
    public static void logPollCycleStart(@NonNull String instanceId, int deviceCount) {
        withMdc(instanceId, 1, () ->
                log.debug("[CYCLE_START] instance='{}', devicesToPoll={}", instanceId, deviceCount)
        );
    }

    /**
     * STEP 8 — Завершение poll-цикла для инстанса.
     */
    public static void logPollCycleEnd(@NonNull String instanceId, int successCount, int failCount, boolean reachable) {
        withMdc(instanceId, 8, () ->
                log.debug("[CYCLE_END] instance='{}', success={}, fail={}, reachable={}",
                        instanceId, successCount, failCount, reachable)
        );
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * STEP 0 — Фабрика создала поллеры.
     */
    public static void logFactoryCreatedPollers(int count) {
        withMdc(null, 0, () ->
                log.debug("[FACTORY] created {} instance poller(s)", count)
        );
    }

    /**
     * STEP 0 — Фабрика создаёт поллер для конкретного инстанса.
     */
    public static void logFactoryCreatingPoller(@NonNull String instanceId) {
        withMdc(instanceId, 0, () ->
                log.debug("[FACTORY] creating poller for instance '{}'", instanceId)
        );
    }

    // ─── TCP Connection ───────────────────────────────────────────────────────

    /**
     * STEP 2 — Попытка создания/переиспользования сокета.
     */
    public static void logSocketCreate(@NonNull String instanceId, @NonNull String host, int port) {
        withMdc(instanceId, 2, () ->
                log.debug("[CONNECT] creating/reusing socket to {}:{}", host, port)
        );
    }

    /**
     * STEP 2 — Сокет успешно подключён.
     */
    public static void logSocketConnected(@NonNull String instanceId, @NonNull String host, int port, int connectTimeoutMs, int readTimeoutMs) {
        withMdc(instanceId, 2, () ->
                log.debug("[CONNECT] socket connected to {}:{}, connectTimeout={}ms, readTimeout={}ms",
                        host, port, connectTimeoutMs, readTimeoutMs)
        );
    }

    /**
     * STEP 2 — Переиспользование существующего сокета.
     */
    public static void logSocketReused(@NonNull String instanceId, @NonNull String host, int port) {
        withMdc(instanceId, 2, () ->
                log.debug("[CONNECT] reusing existing socket to {}:{}", host, port)
        );
    }

    /**
     * STEP 2 — Ошибка сокета.
     */
    public static void logSocketError(@NonNull String instanceId, @NonNull String error) {
        withMdc(instanceId, 2, () ->
                log.warn("[CONNECT] socket error: {}", error)
        );
    }

    /**
     * STEP 2 — Сокет закрыт/инвалидирован.
     */
    public static void logSocketClosed(@NonNull String instanceId) {
        withMdc(instanceId, 2, () ->
                log.debug("[CONNECT] socket invalidated/closed for instance '{}'", instanceId)
        );
    }

    // ─── Request / Response ───────────────────────────────────────────────────

    /**
     * STEP 3 — Формирование и отправка запроса QueryAll.
     */
    public static void logRequestSent(@NonNull String instanceId, @Nullable String device, @NonNull String requestJson) {
        withMdc(instanceId, device, 3, () ->
                log.debug("[REQUEST] device='{}', requestJson={}", device, requestJson)
        );
    }

    /**
     * STEP 3 — Отправка raw bytes (TRACE).
     */
    public static void logRequestBytes(@NonNull String instanceId, int magicLength, int bodyLength) {
        withMdc(instanceId, 3, () ->
                log.trace("[REQUEST] sent magic={} bytes, body={} bytes", magicLength, bodyLength)
        );
    }

    /**
     * STEP 4 — Получение ответа: magic + length.
     */
    public static void logResponseHeader(@NonNull String instanceId, @Nullable String device, int responseLength) {
        withMdc(instanceId, device, 4, () ->
                log.debug("[RESPONSE] device='{}', responseLength={} bytes", device, responseLength)
        );
    }

    /**
     * STEP 4 — Получен некорректный magic header.
     */
    public static void logResponseInvalidMagic(@NonNull String instanceId, @Nullable String device, byte[] magic) {
        withMdc(instanceId, device, 4, () ->
                log.warn("[RESPONSE] device='{}', invalid magic header: bytes={}", device,
                        magic == null ? "null" : java.util.Arrays.toString(magic))
        );
    }

    /**
     * STEP 4 — Получена некорректная длина ответа.
     */
    public static void logResponseInvalidLength(@NonNull String instanceId, @Nullable String device, int length) {
        withMdc(instanceId, device, 4, () ->
                log.warn("[RESPONSE] device='{}', invalid response length: {}", device, length)
        );
    }

    /**
     * STEP 4 — Полный текст ответа (TRACE).
     */
    public static void logResponseBody(@NonNull String instanceId, @Nullable String device, @NonNull String response) {
        withMdc(instanceId, device, 4, () ->
                log.trace("[RESPONSE] device='{}', body={}", device, response)
        );
    }

    // ─── Parse / Map ──────────────────────────────────────────────────────────

    /**
     * STEP 5 — Успешная десериализация JSON → DTO.
     */
    public static void logParseSuccess(@NonNull String instanceId, @Nullable String device, @NonNull String deviceName, int unitCount) {
        withMdc(instanceId, device, 5, () ->
                log.debug("[PARSE] device='{}', parsed deviceName='{}', units={}", device, deviceName, unitCount)
        );
    }

    /**
     * STEP 5 — Ошибка десериализации JSON.
     */
    public static void logParseError(@NonNull String instanceId, @Nullable String device, @NonNull String error, @Nullable String rawResponse) {
        withMdc(instanceId, device, 5, () -> {
            if (rawResponse != null && log.isTraceEnabled()) {
                log.trace("[PARSE] device='{}', rawResponse={}", device, rawResponse);
            }
            log.warn("[PARSE] device='{}', parse error: {}", device, error);
        });
    }

    /**
     * STEP 6 — Успешный маппинг DTO → DeviceSnapshot.
     */
    public static void logMapSuccess(@NonNull String instanceId, @Nullable String device, int unitCount) {
        withMdc(instanceId, device, 6, () ->
                log.debug("[MAP] device='{}', mapped to DeviceSnapshot with {} unit(s)", device, unitCount)
        );
    }

    /**
     * STEP 6 — Ошибка маппинга.
     */
    public static void logMapError(@NonNull String instanceId, @Nullable String device, @NonNull String error) {
        withMdc(instanceId, device, 6, () ->
                log.warn("[MAP] device='{}', mapping error: {}", device, error)
        );
    }

    // ─── Snapshot store ───────────────────────────────────────────────────────

    /**
     * STEP 7 — Snapshot сохранён в хранилище.
     */
    public static void logSnapshotSaved(@NonNull String instanceId, @NonNull String device, int unitCount) {
        withMdc(instanceId, device, 7, () ->
                log.debug("[SAVE] snapshot saved: instance='{}', device='{}', units={}", instanceId, device, unitCount)
        );
    }

    /**
     * STEP 7 — Хранилище очищено для инстанса (все устройства недоступны).
     */
    public static void logSnapshotStoreCleared(@NonNull String instanceId, int removedDeviceCount) {
        withMdc(instanceId, 7, () ->
                log.debug("[SAVE] snapshot store cleared for instance='{}', removedDevices={}", instanceId, removedDeviceCount)
        );
    }

    // ─── Availability ─────────────────────────────────────────────────────────

    /**
     * Инстанс восстановлен (хотя бы одно устройство ответило после недоступности).
     */
    public static void logInstanceRestored(@NonNull String instanceId) {
        withMdc(instanceId, 8, () ->
                log.debug("[AVAILABILITY] instance '{}' connection restored", instanceId)
        );
    }

    /**
     * Инстанс стал недоступен (все устройства не ответили).
     */
    public static void logInstanceUnreachable(@NonNull String instanceId, int deviceCount) {
        withMdc(instanceId, 8, () ->
                log.debug("[AVAILABILITY] instance '{}' unreachable for all {} configured device(s)", instanceId, deviceCount)
        );
    }

    /**
     * Отдельное устройство недоступно (IOException).
     */
    public static void logDeviceUnreachable(@NonNull String instanceId, @NonNull String device, @NonNull String error) {
        withMdc(instanceId, device, 8, () ->
                log.debug("[AVAILABILITY] device='{}' unreachable: {}", device, error)
        );
    }

    /**
     * Инстанс всё ещё недоступен (повторный цикл).
     */
    public static void logInstanceStillUnreachable(@NonNull String instanceId) {
        withMdc(instanceId, 8, () ->
                log.trace("[AVAILABILITY] instance '{}' still unreachable", instanceId)
        );
    }
}
