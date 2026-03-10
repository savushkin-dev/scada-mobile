package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * WebSocket-сообщение типа {@code QUEUE} — вкладка «Очередь».
 *
 * <p>Отправляется клиенту по каналу {@code /ws/unit/{unitId}} при изменении
 * очереди партий в устройстве {@code BatchQueue}. Содержит позиции 1–10.
 *
 * <pre>
 * {
 *   "type": "QUEUE",
 *   "unitId": "trepko2",
 *   "timestamp": "2026-03-01T10:23:45",
 *   "payload": {
 *     "items": [
 *       { "position": 1, "shortCode": "198", "batch": "198", "dateProduced": "10.01.2026" }
 *     ]
 *   }
 * }
 * </pre>
 *
 * @param type      всегда {@code "QUEUE"}
 * @param unitId    идентификатор аппарата
 * @param timestamp ISO-8601 UTC момент формирования
 * @param payload   содержимое очереди
 */
public record QueueMessageDTO(
        String type,
        String unitId,
        String timestamp,
        Payload payload
) {

    @Contract("_, _, _ -> new")
    public static @NonNull QueueMessageDTO of(String unitId, String timestamp, Payload payload) {
        return new QueueMessageDTO("QUEUE", unitId, timestamp, payload);
    }

    /**
     * Очередь партий.
     *
     * @param items позиции очереди (только непустые, отсортированные по позиции)
     */
    public record Payload(List<Item> items) {}

    /**
     * Одна позиция очереди партий.
     * <p>
     * Значения разбираются из строки {@code BatchQueue.ItemXX}
     * формата {@code "Описание | номер партии | дата выработки"}.
     *
     * @param position     номер позиции в очереди (1–10)
     * @param shortCode    краткий код продукта / описание
     * @param batch        номер партии
     * @param dateProduced дата выработки
     */
    public record Item(
            int position,
            @Nullable String shortCode,
            @Nullable String batch,
            @Nullable String dateProduced
    ) {}
}
