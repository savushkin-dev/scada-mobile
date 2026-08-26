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
 */
public record ProductionNotification(
        String unitId,
        String creatorId,
        NotificationCreatorType creatorType,
        boolean active,
        Instant activatedAt,
        Instant deactivatedAt
) {
    /**
     * Создаёт новое активное уведомление от работника.
     *
     * @param unitId    Идентификатор аппарата.
     * @param creatorId Идентификатор работника-создателя.
     * @return Новое активное уведомление с текущим временем активации.
     */
    public static ProductionNotification activate(String unitId, String creatorId) {
        return new ProductionNotification(unitId, creatorId, NotificationCreatorType.USER,
                true, Instant.now(), null);
    }

    /**
     * Создаёт новое активное уведомление от автомата (СКАДА).
     *
     * @param unitId    Идентификатор аппарата.
     * @param machineId PrintSrv instance id автомата (субъект machine-JWT).
     * @return Новое активное уведомление с текущим временем активации.
     */
    public static ProductionNotification activateAsMachine(String unitId, String machineId) {
        return new ProductionNotification(unitId, machineId, NotificationCreatorType.MACHINE,
                true, Instant.now(), null);
    }

    /**
     * Деактивирует текущее уведомление — создаёт копию с {@code active = false}.
     *
     * @return Деактивированная копия с заполненным {@code deactivatedAt}.
     */
    public ProductionNotification deactivate() {
        return new ProductionNotification(unitId, creatorId, creatorType, false, activatedAt, Instant.now());
    }
}
