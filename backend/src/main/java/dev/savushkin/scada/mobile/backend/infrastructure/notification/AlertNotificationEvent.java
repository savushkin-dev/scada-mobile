package dev.savushkin.scada.mobile.backend.infrastructure.notification;

import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Внутреннее Spring Application Event, публикуемое при изменении состояния алёрта аппарата.
 * <p>
 * Является <b>точкой расширения</b> для delivery-адаптеров: любой компонент может
 * подписаться на это событие через {@code @EventListener} и реализовать свой канал
 * доставки (Web Push, SMS, email и т.д.) без изменения основного pipeline.
 *
 * <h3>Состояния</h3>
 * <ul>
 *   <li>{@link State#ACTIVATED} — аппарат вошёл в аварийное состояние
 *       <em>или</em> состав ошибок внутри уже активной аварии изменился.</li>
 *   <li>{@link State#RESOLVED} — аварийное состояние устранено.</li>
 * </ul>
 *
 * <h3>alertId</h3>
 * Стабильный идентификатор уведомления вида {@code "workshopId:unitId:severity:occurredAt"}.
 * Используется для дедупликации повторных попыток доставки.
 *
 * <h3>errorSignature</h3>
 * Хэш состава ошибок, сгруппированных по устройству (см. {@link
 * dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveAlertStore#computeErrorSignature}).
 * Пустая строка для {@code RESOLVED}-события.
 *
 * <h3>route</h3>
 * Готовый маршрут для deep link при клике по системному уведомлению:
 * {@code /workshops/{workshopId}/units/{unitId}/logs}.
 */
public class AlertNotificationEvent extends ApplicationEvent {

    /**
     * Состояние алёрта, которое породило данное событие.
     */
    public enum State {
        /**
         * Аппарат вошёл в аварию или состав ошибок внутри активной аварии изменился.
         */
        ACTIVATED,
        /**
         * Аварийное состояние устранено.
         */
        RESOLVED
    }

    private final State state;
    private final String alertId;
    private final String workshopId;
    private final String unitId;
    private final String unitName;
    private final String severity;
    private final String occurredAt;
    private final String errorSignature;
    private final String route;
    private final List<AlertErrorDTO> errors;

    /**
     * Создаёт событие активации/изменения алёрта.
     *
     * @param source         источник события (обычно {@code this})
     * @param workshopId     идентификатор цеха
     * @param unitId         идентификатор аппарата
     * @param unitName       читаемое название аппарата
     * @param severity       уровень критичности
     * @param occurredAt     ISO-8601 метка времени (UTC)
     * @param errorSignature хэш состава ошибок (для дедупликации)
     * @param errors         список ошибок алёрта
     */
    public static @NonNull AlertNotificationEvent activated(
            @NonNull Object source,
            @NonNull String workshopId,
            @NonNull String unitId,
            @NonNull String unitName,
            @NonNull String severity,
            @NonNull String occurredAt,
            @NonNull String errorSignature,
            @NonNull List<AlertErrorDTO> errors
    ) {
        String alertId = workshopId + ":" + unitId + ":" + severity + ":" + occurredAt;
        String route = "/workshops/" + workshopId + "/units/" + unitId + "/logs";
        return new AlertNotificationEvent(source, State.ACTIVATED,
                alertId, workshopId, unitId, unitName, severity, occurredAt, errorSignature, route, errors);
    }

    /**
     * Создаёт событие разрешения алёрта.
     *
     * @param source     источник события (обычно {@code this})
     * @param workshopId идентификатор цеха
     * @param unitId     идентификатор аппарата
     * @param unitName   читаемое название аппарата
     * @param severity   уровень критичности исходного алёрта
     * @param resolvedAt ISO-8601 метка времени (UTC)
     */
    public static @NonNull AlertNotificationEvent resolved(
            @NonNull Object source,
            @NonNull String workshopId,
            @NonNull String unitId,
            @NonNull String unitName,
            @NonNull String severity,
            @NonNull String resolvedAt
    ) {
        String alertId = workshopId + ":" + unitId + ":" + severity + ":" + resolvedAt;
        String route = "/workshops/" + workshopId + "/units/" + unitId + "/logs";
        return new AlertNotificationEvent(source, State.RESOLVED,
                alertId, workshopId, unitId, unitName, severity, resolvedAt, "", route, List.of());
    }

    private AlertNotificationEvent(
            @NonNull Object source,
            @NonNull State state,
            @NonNull String alertId,
            @NonNull String workshopId,
            @NonNull String unitId,
            @NonNull String unitName,
            @NonNull String severity,
            @NonNull String occurredAt,
            @NonNull String errorSignature,
            @NonNull String route,
            @NonNull List<AlertErrorDTO> errors
    ) {
        super(source);
        this.state = state;
        this.alertId = alertId;
        this.workshopId = workshopId;
        this.unitId = unitId;
        this.unitName = unitName;
        this.severity = severity;
        this.occurredAt = occurredAt;
        this.errorSignature = errorSignature;
        this.route = route;
        this.errors = List.copyOf(errors);
    }

    public @NonNull State getState() { return state; }

    public @NonNull String getAlertId() { return alertId; }

    public @NonNull String getWorkshopId() { return workshopId; }

    public @NonNull String getUnitId() { return unitId; }

    public @NonNull String getUnitName() { return unitName; }

    public @NonNull String getSeverity() { return severity; }

    public @NonNull String getOccurredAt() { return occurredAt; }

    public @NonNull String getErrorSignature() { return errorSignature; }

    public @NonNull String getRoute() { return route; }

    public @NonNull List<AlertErrorDTO> getErrors() { return errors; }

    @Override
    public String toString() {
        return "AlertNotificationEvent{state=" + state
                + ", alertId='" + alertId + '\''
                + ", unitId='" + unitId + '\''
                + ", errors=" + errors.size()
                + '}';
    }
}
