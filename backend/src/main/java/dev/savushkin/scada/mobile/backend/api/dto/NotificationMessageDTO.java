package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus;
import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;

/**
 * WebSocket-сообщение типа {@code NOTIFICATION} — дельта изменения состояния
 * производственного уведомления.
 * <p>
 * Используется в двух ситуациях:
 * <ul>
 *   <li>{@code active = true} — работник создал уведомление (появилось); поле {@code creatorId} заполнено.</li>
 *   <li>{@code active = false} — создатель снял уведомление (исчезло); {@code creatorId} — идентификатор
 *       создателя (для полноты контекста).</li>
 * </ul>
 *
 * <pre>
 * {
 *   "type": "NOTIFICATION",
 *   "unitId": "hassia1",
 *   "unitDbId": 42,
 *   "unitName": "Hassia №1",
 *   "creatorId": "42",
 *   "creatorName": "Иванов Иван Иванович",
 *   "active": true,
 *   "timestamp": "2026-05-09T10:23:45"
 * }
 * </pre>
 *
 * @param type       Всегда {@code "NOTIFICATION"}.
 * @param unitId     ID аппарата/инстанса PrintSrv.
 * @param unitDbId   ID аппарата в БД (units.unit_id), используется для сопоставления с настройками уведомлений.
 * @param unitName   Читаемое название аппарата.
 * @param creatorId  Идентификатор работника, создавшего уведомление.
 * @param creatorName Полное имя (ФИО) работника, создавшего уведомление.
 * @param active     {@code true} — уведомление активно; {@code false} — снято.
 * @param timestamp  ISO-8601 время события (UTC).
 */
public record NotificationMessageDTO(
        String type,
        String unitId,
        Long unitDbId,
        String unitName,
        @Nullable String creatorId,
        @Nullable String creatorName,
        boolean active,
        @Nullable String timestamp,
        @Nullable String sourceMachine,
        @Nullable Long notificationId,
        @Nullable NotificationStatus status,
        @Nullable String acceptedBy,
        @Nullable String acceptedByName,
        @Nullable String acceptedAt,
        long version
) {
    /**
     * Создаёт сообщение об активном (созданном) уведомлении.
     */
    @Contract("_, _, _, _, _, _ -> new")
    public static @NonNull NotificationMessageDTO activated(
            String unitId,
            Long unitDbId,
            String unitName,
            String creatorId,
            String creatorName,
            String timestamp
    ) {
        return new NotificationMessageDTO("NOTIFICATION", unitId, unitDbId, unitName, creatorId, creatorName,
            true, timestamp, unitId, null, NotificationStatus.PENDING, null, null, null, 0L);
    }

    /**
     * Создаёт сообщение о деактивированном (снятом) уведомлении.
     */
    @Contract("_, _, _, _, _, _ -> new")
    public static @NonNull NotificationMessageDTO deactivated(
            String unitId,
            Long unitDbId,
            String unitName,
            String creatorId,
            String creatorName,
            String timestamp
    ) {
        return new NotificationMessageDTO("NOTIFICATION", unitId, unitDbId, unitName, creatorId, creatorName,
            false, timestamp, unitId, null, NotificationStatus.CANCELLED, null, null, null, 0L);
    }

    public static NotificationMessageDTO workflow(
            String unitId,
            Long unitDbId,
            String unitName,
            String creatorId,
            String creatorName,
            ProductionNotification notification,
            @Nullable String acceptedByName,
            String timestamp
    ) {
        return new NotificationMessageDTO("NOTIFICATION", unitId, unitDbId, unitName, creatorId, creatorName,
            notification.status() == NotificationStatus.PENDING
                || notification.status() == NotificationStatus.IN_PROGRESS,
            timestamp, unitId, notification.notificationId(), notification.status(),
            notification.acceptedBy(), acceptedByName, notification.acceptedAt() == null
                ? null : notification.acceptedAt().toString(), notification.version());
    }
}
