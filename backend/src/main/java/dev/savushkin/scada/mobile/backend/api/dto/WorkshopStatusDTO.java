package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Live-статус цеха — рассылается по WebSocket {@code /ws/workshops/status}.
 * <p>
 * Содержит только динамическую часть, которая меняется с каждым scan cycle
 * (какие аппараты в данный момент имеют активные ошибки).
 *
 * @param workshopId   идентификатор цеха (ссылка на {@link WorkshopTopologyDTO#id()})
 * @param problemUnits число аппаратов с активными ошибками на текущий момент
 */
public record WorkshopStatusDTO(
        String workshopId,
        int problemUnits
) {
}
