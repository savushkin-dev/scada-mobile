package dev.savushkin.scada.mobile.backend.domain.model;

import java.time.Instant;

/**
 * Доменный объект состояния производственного уведомления («последняя партия»).
 * <p>
 * Представляет собой <b>toggle-состояние</b>: уведомление существует как активное
 * до тех пор, пока создатель ({@code creatorId}) не деактивирует его.
 * <p>
 * Один аппарат ({@code unitId}) может иметь не более одного активного уведомления одновременно.
 * Это инвариант, обеспечиваемый на уровне {@code NotificationRepository}.
 *
 * <h3>Жизненный цикл</h3>
 * <ol>
 *   <li><b>Создание:</b> работник нажимает FAB либо СКАДА отправляет сигнал с автомата →
 *       {@code ProductionNotification} создаётся с {@code active = true}, {@code deactivatedAt = null}.</li>
 *   <li><b>Активно:</b> уведомление рассылается всем подписанным через WebSocket.</li>
 *   <li><b>Деактивация:</b> создатель повторно отправляет toggle → {@code active = false},
 *       {@code deactivatedAt} заполняется. Уведомление исчезает у всех.</li>
 * </ol>
 *
 * <h3>Immutable</h3>
 * Record — неизменяемый. Для деактивации создаётся новый экземпляр
 * с {@code active = false}.
 *
 * @param unitId          Идентификатор аппарата (PrintSrv instance id, напр. "hassia1").
 * @param creatorId       Идентификатор создателя: для {@code USER} — числовой id работника,
 *                        для {@code MACHINE} — PrintSrv instance id автомата.
 * @param creatorType     Тип создателя ({@link NotificationCreatorType}).
 * @param active          {@code true} — уведомление активно; {@code false} — деактивировано.
 * @param activatedAt     Время активации (ISO-8601 / {@link Instant}).
 * @param deactivatedAt   Время деактивации ({@code null} пока активно).
 * @param curItem         Значение CurItem (текущая партия/изделие) на момент активации;
 *                        {@code null}, если на момент активации значение было недоступно.
 */
public record ProductionNotification(
    Long notificationId,
        String unitId,
        String creatorId,
        NotificationCreatorType creatorType,
    NotificationStatus status,
        boolean active,
        Instant activatedAt,
    Instant deactivatedAt,
    String acceptedBy,
    Instant acceptedAt,
    Instant completedAt,
    Instant cancelledAt,
    long version,
    String curItem
) {
    public ProductionNotification(
        String unitId,
        String creatorId,
        NotificationCreatorType creatorType,
        boolean active,
        Instant activatedAt,
        Instant deactivatedAt
    ) {
        this(unitId, creatorId, creatorType, active, activatedAt, deactivatedAt, null);
    }

    public ProductionNotification(
        String unitId,
        String creatorId,
        NotificationCreatorType creatorType,
        boolean active,
        Instant activatedAt,
        Instant deactivatedAt,
        String curItem
    ) {
    this(null, unitId, creatorId, creatorType,
        active ? NotificationStatus.PENDING : NotificationStatus.COMPLETED,
        active, activatedAt, deactivatedAt, null, null,
        active ? null : deactivatedAt, active ? null : deactivatedAt, 0L, curItem);
    }

    /**
     * Создаёт новое активное уведомление от работника.
     *
     * @param unitId    Идентификатор аппарата.
     * @param creatorId Идентификатор работника-создателя.
     * @return Новое активное уведомление с текущим временем активации.
     */
    public static ProductionNotification activate(String unitId, String creatorId) {
        return activate(unitId, creatorId, null);
    }

    /**
     * Создаёт новое активное уведомление от работника с фиксацией текущей партии.
     *
     * @param unitId    Идентификатор аппарата.
     * @param creatorId Идентификатор работника-создателя.
     * @param curItem   Значение CurItem на момент активации (может быть {@code null}).
     * @return Новое активное уведомление с текущим временем активации.
     */
    public static ProductionNotification activate(String unitId, String creatorId, String curItem) {
        return new ProductionNotification(unitId, creatorId, NotificationCreatorType.USER,
                true, Instant.now(), null, curItem);
    }

    /**
     * Создаёт новое активное уведомление от автомата (СКАДА).
     *
     * @param unitId    Идентификатор аппарата.
     * @param machineId PrintSrv instance id автомата (субъект machine-JWT).
     * @return Новое активное уведомление с текущим временем активации.
     */
    public static ProductionNotification activateAsMachine(String unitId, String machineId) {
        return activateAsMachine(unitId, machineId, null);
    }

    /**
     * Создаёт новое активное уведомление от автомата (СКАДА) с фиксацией текущей партии.
     *
     * @param unitId    Идентификатор аппарата.
     * @param machineId PrintSrv instance id автомата (субъект machine-JWT).
     * @param curItem   Значение CurItem на момент активации (может быть {@code null}).
     * @return Новое активное уведомление с текущим временем активации.
     */
    public static ProductionNotification activateAsMachine(String unitId, String machineId, String curItem) {
        return new ProductionNotification(unitId, machineId, NotificationCreatorType.MACHINE,
                true, Instant.now(), null, curItem);
    }

    /**
     * Деактивирует текущее уведомление — создаёт копию с {@code active = false}.
     *
     * @return Деактивированная копия с заполненным {@code deactivatedAt}.
     */
    public ProductionNotification deactivate() {
        return cancel(creatorId);
    }

    public ProductionNotification accept(String userId) {
        requireStatus(NotificationStatus.PENDING, "принять");
        Instant now = Instant.now();
        return new ProductionNotification(notificationId, unitId, creatorId, creatorType,
                NotificationStatus.IN_PROGRESS, true, activatedAt, deactivatedAt,
                userId, now, completedAt, cancelledAt, version + 1, curItem);
    }

    public ProductionNotification complete(String actorId) {
        requireStatus(NotificationStatus.IN_PROGRESS, "завершить");
        if (!creatorId.equals(actorId) && !actorId.equals(acceptedBy)) {
            throw new NotificationTransitionException("Завершить уведомление может только создатель или исполнитель");
        }
        Instant now = Instant.now();
        return new ProductionNotification(notificationId, unitId, creatorId, creatorType,
                NotificationStatus.COMPLETED, false, activatedAt, now,
                acceptedBy, acceptedAt, now, cancelledAt, version + 1, curItem);
    }

    public ProductionNotification cancel(String actorId) {
        requireStatus(NotificationStatus.PENDING, "отменить");
        if (!creatorId.equals(actorId)) {
            throw new NotificationTransitionException("Отменить уведомление может только создатель");
        }
        Instant now = Instant.now();
        return new ProductionNotification(notificationId, unitId, creatorId, creatorType,
                NotificationStatus.CANCELLED, false, activatedAt, now,
                acceptedBy, acceptedAt, completedAt, now, version + 1, curItem);
    }

    private void requireStatus(NotificationStatus expected, String operation) {
        if (status != expected) {
            throw new NotificationTransitionException(
                    "Нельзя %s уведомление в статусе %s".formatted(operation, status));
        }
    }

    public static class NotificationTransitionException extends IllegalStateException {
        public NotificationTransitionException(String message) {
            super(message);
        }
    }
}
