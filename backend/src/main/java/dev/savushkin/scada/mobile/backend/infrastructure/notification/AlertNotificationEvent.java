package dev.savushkin.scada.mobile.backend.infrastructure.notification;

import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import org.jspecify.annotations.NonNull;
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
 * Поддерживается только {@link State#ACTIVATED}, так как Web Push в проекте
 * отправляется только при появлении/изменении активной аварии.
 *
 * <h3>alertId</h3>
 * Стабильный идентификатор уведомления вида {@code "unitId:firstSeenAt"}.
 * Используется для дедупликации повторных попыток доставки.
 *
 * <h3>errorSignature</h3>
 * Хэш состава ошибок, сгруппированных по устройству (см. {@link
 * dev.savushkin.scada.mobile.backend.infrastructure.store.ActiveAlertStore#computeErrorSignature}).
 * Используется для дедупликации повторных уведомлений при изменении состава ошибок.
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
        ACTIVATED
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
    private final String groupKey;
    private final String notificationTag;
    private final List<AlertErrorDTO> errors;

    /**
     * Создаёт событие активации/изменения алёрта.
     *
     * @param source         источник события (обычно {@code this})
     * @param workshopId     идентификатор цеха
     * @param unitId         идентификатор аппарата
     * @param unitName       читаемое название аппарата
    * @param severity       уровень критичности
    * @param firstSeenAt    ISO-8601 метка времени (UTC) первого появления аварии
     * @param errorSignature хэш состава ошибок (для дедупликации)
     * @param errors         список ошибок алёрта
     */
    public static @NonNull AlertNotificationEvent activated(
            @NonNull Object source,
            @NonNull String workshopId,
            @NonNull String unitId,
            @NonNull String unitName,
            @NonNull String severity,
            @NonNull String firstSeenAt,
            @NonNull String errorSignature,
            @NonNull List<AlertErrorDTO> errors
    ) {
        String alertId = unitId + ":" + firstSeenAt;
        String route = "/workshops/" + workshopId + "/units/" + unitId + "/logs";
        String groupKey = workshopId + ":" + unitId;
        String notificationTag = "workshop:" + workshopId + ":unit:" + unitId;
        return new AlertNotificationEvent(source, State.ACTIVATED,
                alertId, workshopId, unitId, unitName, severity, firstSeenAt,
                errorSignature, route, groupKey, notificationTag, errors);
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
            @NonNull String groupKey,
            @NonNull String notificationTag,
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
        this.groupKey = groupKey;
        this.notificationTag = notificationTag;
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

    public @NonNull String getGroupKey() { return groupKey; }

    public @NonNull String getNotificationTag() { return notificationTag; }

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
