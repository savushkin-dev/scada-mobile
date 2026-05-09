package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.api.dto.NotificationMessageDTO;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище активных уведомлений для WebSocket.
 * <p>
 * Выполняет две функции:
 * <ol>
 *   <li><b>Snapshot при коннекте</b> — {@link #getAll()} возвращает список всех текущих
 *       активных уведомлений; {@code LiveWsHandler} отправляет его новым клиентам как
 *       {@code NOTIFICATION_SNAPSHOT} сразу после установки соединения.</li>
 *   <li><b>Дельта-детектор</b> — {@link #updateAndDiff(String, NotificationMessageDTO)} атомарно
 *       обновляет состояние одного аппарата и возвращает дельту для live-рассылки.</li>
 * </ol>
 * <p>
 * <b>Важно:</b> Это отдельный WS-projection store от {@code InMemoryNotificationStore}
 * (реализация {@code NotificationRepository}). {@code ActiveNotificationStore} — WS-оптимизированная
 * проекция, {@code NotificationRepository} — порт для бизнес-логики.
 * <p>
 * Данные хранятся в {@link ConcurrentHashMap} (ключ — {@code unitId}).
 * Метод {@link #updateAndDiff(String, NotificationMessageDTO)} синхронизирован для атомарной пары
 * «сравни + обнови» на уровне конкретного аппарата.
 */
@Component
public class ActiveNotificationStore {

    /**
     * unitId → активное уведомление (WS-projection)
     */
    private final ConcurrentHashMap<String, NotificationMessageDTO> store = new ConcurrentHashMap<>();

    /**
     * Возвращает снимок всех текущих активных уведомлений.
     * Потокобезопасен — возвращает неизменяемую копию.
     */
    public List<NotificationMessageDTO> getAll() {
        return List.copyOf(store.values());
    }

    /**
     * Атомарно обновляет состояние одного аппарата и возвращает локальную дельту.
     *
     * <p>По аналогии с {@link ActiveAlertStore#updateAndDiff}:
     * <ul>
     *   <li>Если уведомление стало активным (ранее не было или было inactive) — {@code added} непуст.</li>
     *   <li>Если уведомление стало inactive (ранее было active) — {@code removed} непуст.</li>
     *   <li>Без изменений — пустая дельта.</li>
     * </ul>
     *
     * @param unitId   идентификатор аппарата
     * @param incoming текущее состояние уведомления или {@code null}, если уведомления нет
     * @return локальная дельта по одному аппарату
     */
    public synchronized Delta updateAndDiff(
            @NonNull String unitId,
            NotificationMessageDTO incoming
    ) {
        NotificationMessageDTO existing = store.get(unitId);

        // Входящее — inactive (уведомление снято)
        if (!incoming.active()) {
            if (existing != null) {
                store.remove(unitId);
                return new Delta(List.of(), List.of(incoming));
            }
            return new Delta(List.of(), List.of());
        }

        // Входящее — active (уведомление создано)
        if (existing == null) {
            store.put(unitId, incoming);
            return new Delta(List.of(incoming), List.of());
        }

        // Уже активно — без изменений
        return new Delta(List.of(), List.of());
    }

    /**
     * Результат сравнения двух срезов уведомлений.
     *
     * @param added   Уведомления, которые появились в текущем срезе (отсутствовали ранее).
     * @param removed Уведомления, которые исчезли (были активны, теперь нет).
     */
    public record Delta(List<NotificationMessageDTO> added, List<NotificationMessageDTO> removed) {
    }
}
