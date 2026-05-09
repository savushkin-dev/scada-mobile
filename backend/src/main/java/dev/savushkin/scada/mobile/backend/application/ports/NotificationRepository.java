package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.ProductionNotification;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Порт хранения производственных уведомлений.
 * <p>
 * Интерфейс определяет контракт для persistence-слоя без привязки к конкретной СУБД.
 * Текущая реализация — {@code InMemoryNotificationStore} (ConcurrentHashMap).
 * Будущая реализация — PostgreSQL / JPA.
 *
 * <h3>Инвариант</h3>
 * На один {@code unitId} существует не более одного активного уведомления ({@code active = true}).
 * Метод {@link #save} гарантирует замену предыдущего активного при сохранении нового.
 */
public interface NotificationRepository {

    /**
     * Ищет активное (не деактивированное) уведомление для данного аппарата.
     *
     * @param unitId Идентификатор аппарата.
     * @return Активное уведомление, или {@code Optional.empty()} если нет активного.
     */
    @NonNull Optional<ProductionNotification> findActiveByUnitId(@NonNull String unitId);

    /**
     * Возвращает все активные уведомления (от всех аппаратов).
     * Используется для построения снимка (snapshot) при подключении нового WS-клиента.
     *
     * @return Неизменяемый список активных уведомлений.
     */
    @NonNull List<ProductionNotification> findAllActive();

    /**
     * Сохраняет (создаёт или заменяет) уведомление для данного аппарата.
     * <p>
     * При сохранении нового активного уведомления предыдущее активное для того же {@code unitId}
     * заменяется. Деактивированные уведомления хранятся только в текущем in-memory сценарии
     * и могут быть очищены реализацией.
     *
     * @param notification Уведомление для сохранения (не {@code null}).
     */
    void save(@NonNull ProductionNotification notification);

    /**
     * Деактивирует активное уведомление для аппарата (если есть).
     * <p>
     * Если активного уведомления нет — операция является no-op.
     *
     * @param unitId Идентификатор аппарата.
     */
    void deactivateByUnitId(@NonNull String unitId);
}
