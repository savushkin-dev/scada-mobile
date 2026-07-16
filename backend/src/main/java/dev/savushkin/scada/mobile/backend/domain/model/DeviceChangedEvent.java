package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил связь устройства с автоматом (unit_devices).
 * <p>
 * Для операции DELETE поля {@code unitId} и {@code printsrvInstanceId}
 * заполняются из данных, прочитанных перед удалением, чтобы слушатель мог
 * корректно инвалидировать топологию затронутого аппарата.
 */
public record DeviceChangedEvent(Long deviceId, Long unitId, String printsrvInstanceId, ChangeAction action) {
}
