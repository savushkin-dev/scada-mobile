package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe трекер состояния соединения для одного инстанса PrintSrv.
 *
 * <p>Не является Spring-бином: создаётся фабрично через {@link PrintSrvPollerFactory}
 * и живёт внутри {@link PrintSrvInstancePoller}.
 *
 * <p>Хранит только счётчик последовательных сбоев — минимально необходимое
 * состояние для умного логирования (первый сбой → WARN, последующие → DEBUG,
 * восстановление → INFO).
 *
 * <h3>Thread-safety</h3>
 * {@link AtomicInteger} обеспечивает lock-free атомарность всех операций.
 */
public final class InstanceConnectionState {

    private final String instanceId;

    /**
     * Счётчик последовательных сбоев. Сбрасывается при любом успехе.
     */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public InstanceConnectionState(String instanceId) {
        this.instanceId = instanceId;
    }

    // ─── Геттеры ──────────────────────────────────────────────────────────────

    public String getInstanceId() {
        return instanceId;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    // ─── Переходы ─────────────────────────────────────────────────────────────

    /**
     * Фиксирует успешный poll.
     *
     * @return {@code true}, если инстанс только что восстановился после серии сбоев
     * (т.е. предыдущее состояние было failing). Используется для
     * log-сообщения "connection restored".
     */
    public boolean recordSuccess() {
        return consecutiveFailures.getAndSet(0) > 0;
    }

    /**
     * Фиксирует неудачный poll.
     *
     * @return новое значение счётчика после инкремента
     */
    public int recordFailure() {
        return consecutiveFailures.incrementAndGet();
    }
}
