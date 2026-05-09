package dev.savushkin.scada.mobile.backend.application.ports;

import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Порт хранения привязок пользователей к аппаратам.
 * <p>
 * Определяет:
 * <ul>
 *   <li>К каким аппаратам работник <b>закреплён</b> (и может отправлять уведомления).</li>
 *   <li>От каких аппаратов работник <b>получает</b> уведомления (подписки).</li>
 * </ul>
 * <p>
 * Интерфейс определяет доменный контракт. Текущая реализация — {@code InMemoryUserAssignmentStore}
 * с seed-данными из YAML. Будущая — PostgreSQL (таблица {@code user_unit_assignments}).
 *
 * <h3>Инвариант</h3>
 * Если {@link #canSendNotification} возвращает {@code true}, то {@code unitId}
 * обязательно входит в {@link #getAssignedUnitIds} (закреплён = может отправлять).
 * Подписка может быть шире или уже закрепления — управляется отдельно.
 */
public interface UserAssignmentRepository {

    /**
     * Проверяет, имеет ли работник право отправлять уведомления от данного аппарата.
     * <p>
     * В текущей реализации: работник закреплён за аппаратом ({@code unitId ∈ assignedUnitIds})
     * ИЛИ включён режим {@code defaultAccess: all}.
     *
     * @param userId Идентификатор работника.
     * @param unitId Идентификатор аппарата.
     * @return {@code true} — работник имеет право; {@code false} — нет.
     */
    boolean canSendNotification(@NonNull String userId, @NonNull String unitId);

    /**
     * Возвращает множество аппаратов, на уведомления от которых подписан работник.
     * <p>
     * Используется для фильтрации {@code NOTIFICATION_SNAPSHOT} — клиент получает
     * только релевантные уведомления.
     *
     * @param userId Идентификатор работника.
     * @return Неизменяемое множество идентификаторов аппаратов.
     */
    @NonNull Set<String> getSubscribedUnitIds(@NonNull String userId);

    /**
     * Возвращает множество аппаратов, за которыми закреплён работник.
     * <p>
     * Закрепление = право отправлять уведомления. Может совпадать с подпиской,
     * но не обязано.
     *
     * @param userId Идентификатор работника.
     * @return Неизменяемое множество идентификаторов аппаратов.
     */
    @NonNull Set<String> getAssignedUnitIds(@NonNull String userId);
}
