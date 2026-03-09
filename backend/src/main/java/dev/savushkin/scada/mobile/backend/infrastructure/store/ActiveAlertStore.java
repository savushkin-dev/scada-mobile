package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище активных алёртов.
 * <p>
 * Выполняет две функции:
 * <ol>
 *   <li><b>Snapshot при коннекте</b> — {@link #getAll()} возвращает список всех текущих
 *       активных алёртов; {@code LiveWsHandler} отправляет его новым клиентам как
 *       {@code ALERT_SNAPSHOT} сразу после установки соединения.</li>
 *   <li><b>Дельта-детектор</b> — {@link #updateAndDiff(String, AlertMessageDTO)} атомарно
 *       обновляет состояние одного аппарата и возвращает дельту для live-рассылки.</li>
 * </ol>
 * <p>
 * Данные хранятся в {@link ConcurrentHashMap} (ключ — {@code unitId}).
 * Метод {@link #updateAndDiff(String, AlertMessageDTO)} синхронизирован для атомарной пары
 * «сравни + обнови» на уровне конкретного аппарата.
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
     * Атомарно обновляет состояние одного аппарата и возвращает локальную дельту.
     *
     * <p>Существующие алёрты не перезаписываются: если алёрт уже активен,
     * сохраняется его исходный timestamp первого обнаружения.
     *
     * @param unitId        идентификатор аппарата
     * @param currentAlert  текущий активный алёрт или {@code null}, если алёрта нет
     * @return локальная дельта по одному аппарату
     */
    public synchronized Delta updateAndDiff(@NonNull String unitId, AlertMessageDTO currentAlert) {
        AlertMessageDTO existing = store.get(unitId);

        if (currentAlert == null) {
            if (existing == null) {
                return new Delta(List.of(), List.of());
            }

            store.remove(unitId);
            return new Delta(List.of(), List.of(existing));
        }

        if (existing == null) {
            store.put(unitId, currentAlert);
            return new Delta(List.of(currentAlert), List.of());
        }

        return new Delta(List.of(), List.of());
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
