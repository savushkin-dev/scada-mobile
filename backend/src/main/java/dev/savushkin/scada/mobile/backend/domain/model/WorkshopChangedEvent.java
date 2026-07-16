package dev.savushkin.scada.mobile.backend.domain.model;

/**
 * Доменное событие: администратор изменил данные цеха.
 */
public record WorkshopChangedEvent(Long workshopId, ChangeAction action) {
}
