package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * WebSocket-сообщение типа {@code NOTIFICATION} — дельта изменения состояния
 * производственного уведомления.
 * <p>
 * Используется в двух ситуациях:
 * <ul>
 *   <li>{@code active = true} — работник создал уведомление (оявилось); поле {@code creatorId} заполнено.</li>
 *   <li>{@code active = false} — создатель снял уведомление (исчезло); {@code creatorId} — идентификатор
 *       создателя (для полноты контекста).</li>
 * </ul>
 *
 * <pre>
 * {
 *   "type": "NOTIFICATION",
 *   "unitId": "hassia1",
 *   "unitName": "Hassia №1",
 *   "creatorId": "ivanov",
 *   "active": true,
 *   "timestamp": "2026-05-09T10:23:45"
 * }
 * </pre>
 *
 * @param type       Всегда {@code "NOTIFICATION"}.
 * @param unitId     ID аппарата/инстанса PrintSrv.
 * @param unitName   Читаемое название аппарата.
 * @param creatorId  Идентификатор работника, создавшего уведомление.
 * @param active     {@code true} — уведомление активно; {@code false} — снято.
 * @param timestamp  ISO-8601 время события (UTC).
 */
public record NotificationMessageDTO(
        String type,
        String unitId,
        String unitName,
        @Nullable String creatorId,
        boolean active,
        @Nullable String timestamp
) {
    /**
     * Создаёт сообщение об активном (созданном) уведомлении.
     */
    @Contract("_, _, _, _ -> new")
    public static @NonNull NotificationMessageDTO activated(
            String unitId,
            String unitName,
            String creatorId,
            String timestamp
    ) {
        return new NotificationMessageDTO("NOTIFICATION", unitId, unitName, creatorId, true, timestamp);
    }

    /**
     * Создаёт сообщение о деактивированном (снятом) уведомлении.
     */
    @Contract("_, _, _, _ -> new")
    public static @NonNull NotificationMessageDTO deactivated(
            String unitId,
            String unitName,
            String creatorId,
            String timestamp
    ) {
        return new NotificationMessageDTO("NOTIFICATION", unitId, unitName, creatorId, false, timestamp);
    }
}
