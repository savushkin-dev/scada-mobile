package dev.savushkin.scada.mobile.backend.api.dto;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * WebSocket-сообщение типа {@code NOTIFICATION_SNAPSHOT} — начальный снимок всех
 * активных производственных уведомлений, отправляемый клиенту сразу после
 * установки соединения.
 * <p>
 * Клиент использует этот снимок как стартовое состояние для {@code Map<unitId, NotificationData>}
 * в глобальном стейте. При реконнекте снимок полностью заменяет текущее состояние
 * (тем самым восстанавливая корректное состояние).
 *
 * <pre>
 * {
 *   "type": "NOTIFICATION_SNAPSHOT",
 *   "payload": [
 *     {
 *       "type": "NOTIFICATION",
 *       "unitId": "hassia1",
 *       "unitName": "Hassia №1",
 *       "creatorId": "ivanov",
 *       "active": true,
 *       "timestamp": "2026-05-09T10:23:45"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @param type    Всегда {@code "NOTIFICATION_SNAPSHOT"}.
 * @param payload Список активных уведомлений на момент подключения. Может быть пустым.
 */
public record NotificationSnapshotMessageDTO(
        String type,
        List<NotificationMessageDTO> payload
) {
    /**
     * Фабричный метод — тип всегда фиксирован.
     */
    public static @NonNull NotificationSnapshotMessageDTO of(List<NotificationMessageDTO> notifications) {
        return new NotificationSnapshotMessageDTO("NOTIFICATION_SNAPSHOT", notifications);
    }
}
