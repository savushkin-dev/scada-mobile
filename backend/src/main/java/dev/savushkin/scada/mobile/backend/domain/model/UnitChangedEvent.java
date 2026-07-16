package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил данные автомата (unit).
 * <p>
 * Для операции DELETE поля {@code printsrvInstanceId} и {@code workshopId}
 * заполняются из данных, прочитанных перед удалением, чтобы слушатель мог
 * корректно уведомить клиентов о затронутой топологии.
 */
public record UnitChangedEvent(Long unitId, String printsrvInstanceId, Long workshopId, ChangeAction action) {
}
