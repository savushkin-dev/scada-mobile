package dev.savushkin.scada.mobile.backend.infrastructure.store;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасный трекер времени начала простоя аппаратов.
 * <p>
 * Фиксирует {@link Instant} момента появления алёрта (ошибки или остановки) для каждого
 * инстанса и позволяет вычислить прошедшее время в формате {@code HH:MM:SS} —
 * для отображения в таймере на карточке аппарата ({@link dev.savushkin.scada.mobile.backend.api.dto.UnitStatusDTO#timer()}).
 * <p>
 * Жизненный цикл таймера:
 * <ol>
 *   <li>{@link #onAlertStarted(String)} — вызывается из {@code StatusBroadcaster} при появлении
 *       нового алёрта (элемент {@code Delta.added}). Использует {@link ConcurrentHashMap#putIfAbsent},
 *       поэтому если аппарат уже в простое — время старта <b>не перезаписывается</b>.</li>
 *   <li>{@link #onAlertResolved(String)} — вызывается при исчезновении алёрта
 *       (элемент {@code Delta.removed}). Сбрасывает таймер.</li>
 * </ol>
 * <p>
 * <b>Ограничение:</b> хранилище in-memory. При рестарте приложения времена сбрасываются.
 * Для долгосрочной персистентности потребуется внешнее хранилище.
 */
@Component
public class DowntimeTracker {

    /**
     * instanceId → момент начала текущего простоя
     */
    private final ConcurrentHashMap<String, Instant> startTimes = new ConcurrentHashMap<>();

    /**
     * Фиксирует начало простоя для аппарата.
     * <p>
     * Идемпотентен: если простой уже отслеживается — время старта не перезаписывается.
     *
     * @param unitId идентификатор инстанса (аппарата)
     */
    public void onAlertStarted(String unitId) {
        startTimes.putIfAbsent(unitId, Instant.now());
    }

    /**
     * Сбрасывает таймер простоя для аппарата (аппарат вернулся в работу).
     *
     * @param unitId идентификатор инстанса (аппарата)
     */
    public void onAlertResolved(String unitId) {
        startTimes.remove(unitId);
    }

    /**
     * Возвращает длительность текущего простоя в формате {@code HH:MM:SS}.
     * <p>
     * Если простой для данного аппарата не отслеживается — возвращает {@link Optional#empty()}.
     *
     * @param unitId идентификатор инстанса (аппарата)
     * @return строка вида {@code "01:23:45"} или пустой {@link Optional}
     */
    public Optional<String> getElapsedFormatted(String unitId) {
        Instant start = startTimes.get(unitId);
        if (start == null) {
            return Optional.empty();
        }
        long totalSeconds = Duration.between(start, Instant.now()).toSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return Optional.of(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    /**
     * Возвращает абсолютный момент начала простоя.
     * <p>
     * Используется, если клиенту нужен ISO-timestamp начала события
     * (например, в будущих расширениях API).
     *
     * @param unitId идентификатор инстанса (аппарата)
     * @return момент начала простоя или пустой {@link Optional}
     */
    public Optional<Instant> getStartTime(String unitId) {
        return Optional.ofNullable(startTimes.get(unitId));
    }
}
