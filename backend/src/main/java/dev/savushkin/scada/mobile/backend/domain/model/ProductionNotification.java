package dev.savushkin.scada.mobile.backend.domain.model;

import java.time.Instant;

/**
 * Доменный объект состояния производственного уведомления.
 * <p>
 * Представляет собой <b>toggle-состояние</b>: уведомление существует как активное
 * до тех пор, пока создатель ({@code creatorId}) не деактивирует его.
 * <p>
 * Один аппарат ({@code unitId}) может иметь не более одного активного уведомления одновременно.
 * Это инвариант, обеспечиваемый на уровне {@code NotificationRepository}.
 *
 * <h3>Жизненный цикл</h3>
 * <ol>
 *   <li><b>Создание:</b> работник нажимает FAB → {@code ProductionNotification} создаётся
 *       с {@code active = true}, {@code deactivatedAt = null}.</li>
 *   <li><b>Активно:</b> уведомление рассылается всем подписанным через WebSocket.</li>
 *   <li><b>Деактивация:</b> создатель повторно нажимает FAB → {@code active = false},
 *       {@code deactivatedAt} заполняется. Уведомление исчезает у всех.</li>
 * </ol>
 *
 * <h3>Immutable</h3>
 * Record — неизменяемый. Для деактивации создаётся новый экземпляр
 * с {@code active = false}.
 *
 * @param unitId          Идентификатор аппарата (PrintSrv instance id, напр. "hassia1").
 * @param creatorId       Идентификатор работника, создавшего уведомление.
 * @param active          {@code true} — уведомление активно; {@code false} — деактивировано.
 * @param activatedAt     Время активации (ISO-8601 / {@link Instant}).
 * @param deactivatedAt   Время деактивации ({@code null} пока активно).
 */
public record ProductionNotification(
        String unitId,
        String creatorId,
        boolean active,
        Instant activatedAt,
        Instant deactivatedAt
) {
    /**
     * Создаёт новое активное уведомление.
     *
     * @param unitId    Идентификатор аппарата.
     * @param creatorId Идентификатор работника-создателя.
     * @return Новое активное уведомление с текущим временем активации.
     */
    public static ProductionNotification activate(String unitId, String creatorId) {
        return new ProductionNotification(unitId, creatorId, true, Instant.now(), null);
    }

    /**
     * Деактивирует текущее уведомление — создаёт копию с {@code active = false}.
     *
     * @return Деактивированная копия с заполненным {@code deactivatedAt}.
     */
    public ProductionNotification deactivate() {
        return new ProductionNotification(unitId, creatorId, false, activatedAt, Instant.now());
    }
}
