package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище активных алёртов.
 * <p>
 * Выполняет две функции:
 * <ol>
 *   <li><b>Snapshot при коннекте</b> — {@link #getAll()} возвращает список всех текущих
 *       активных алёртов; {@code LiveWsHandler} отправляет его новым клиентам как
 *       {@code ALERT_SNAPSHOT} сразу после установки соединения.</li>
 *   <li><b>Дельта-детектор</b> — {@link #updateAndDiff(Map)} сравнивает переданный
 *       текущий срез с предыдущим состоянием и возвращает добавленные и исчезнувшие алёрты.
 *       {@code StatusBroadcaster} использует результат для рассылки {@code ALERT} дельт.</li>
 * </ol>
 * <p>
 * Данные хранятся в {@link ConcurrentHashMap} (ключ — {@code unitId}).
 * Метод {@link #updateAndDiff(Map)} синхронизирован для атомарной пары «сравни + обнови»,
 * что предотвращает потерю дельт при параллельных scan cycles (которые в текущей архитектуре
 * не происходят, но могут появиться при добавлении {@code @Async}).
 */
@Component
public class ActiveAlertStore {

    /**
     * unitId → активный алёрт
     */
    private final ConcurrentHashMap<String, AlertMessageDTO> store = new ConcurrentHashMap<>();

    /**
     * Возвращает снимок всех текущих активных алёртов.
     * Потокобезопасен — возвращает неизменяемую копию.
     */
    public List<AlertMessageDTO> getAll() {
        return List.copyOf(store.values());
    }

    /**
     * Атомарно сравнивает переданный срез с текущим состоянием,
     * обновляет внутреннее состояние и возвращает дельту.
     * <p>
     * Существующие алёрты <b>не перезаписываются</b>: если алёрт уже был в хранилище,
     * его оригинальный {@code timestamp} первого обнаружения сохраняется.
     * Это позволяет клиентам использовать {@code timestamp} как {@code startedAt}
     * при отображении ALERT_SNAPSHOT (снапшота при подключении).
     *
     * @param current текущий срез алёртов ({@code unitId → AlertMessageDTO})
     * @return {@link Delta} с двумя списками: появившиеся и исчезнувшие алёрты
     */
    public synchronized Delta updateAndDiff(Map<String, AlertMessageDTO> current) {
        List<AlertMessageDTO> added = new ArrayList<>();
        List<AlertMessageDTO> removed = new ArrayList<>();

        // Появившиеся: есть в current, нет в store
        for (var entry : current.entrySet()) {
            if (!store.containsKey(entry.getKey())) {
                added.add(entry.getValue());
            }
        }

        // Исчезнувшие: есть в store, нет в current
        for (var entry : store.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                removed.add(entry.getValue());
            }
        }

        // Обновляем хранилище: удаляем исчезнувшие, добавляем только новые.
        // Существующие алёрты НЕ перезаписываем — это сохраняет оригинальный
        // timestamp первого обнаружения, который клиент использует как "startedAt".
        store.keySet().retainAll(current.keySet());
        for (var entry : current.entrySet()) {
            store.putIfAbsent(entry.getKey(), entry.getValue());
        }

        return new Delta(added, removed);
    }

    /**
     * Результат сравнения двух срезов алёртов.
     *
     * @param added   Алёрты, которые появились в текущем срезе (отсутствовали ранее).
     * @param removed Алёрты, которые исчезли (были активны, теперь нет).
     */
    public record Delta(List<AlertMessageDTO> added, List<AlertMessageDTO> removed) {
    }
}
