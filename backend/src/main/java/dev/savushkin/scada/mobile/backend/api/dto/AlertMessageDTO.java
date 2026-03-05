package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * WebSocket-сообщение типа {@code ALERT} — дельта изменения состояния тревоги.
 * <p>
 * Используется в двух ситуациях:
 * <ul>
 *   <li>{@code active = true} — ошибка появилась; поле {@code errors} содержит детали.</li>
 *   <li>{@code active = false} — ошибка исчезла; поле {@code errors} пустое.</li>
 * </ul>
 *
 * <pre>
 * {
 *   "type": "ALERT",
 *   "workshopId": "apparatniy",
 *   "unitId": "hassia2",
 *   "unitName": "Линия розлива ПЭТ №2",
 *   "severity": "Critical",
 *   "active": true,
 *   "errors": [{"device":"Line","code":0,"message":"Ошибка"}],
 *   "timestamp": "2026-03-05T10:23:45"
 * }
 * </pre>
 *
 * @param type       Всегда {@code "ALERT"}.
 * @param workshopId ID цеха (для перекраски карточки цеха на дашборде).
 * @param unitId     ID аппарата.
 * @param unitName   Читаемое название аппарата (для текста push-уведомления).
 * @param severity   Уровень критичности: {@code "Critical"} или {@code "Warning"}.
 * @param active     {@code true} — ошибка активна, {@code false} — устранена.
 * @param errors     Список ошибок. Пустой при {@code active = false}.
 * @param timestamp  ISO-8601 время события (UTC).
 */
public record AlertMessageDTO(
        String type,
        String workshopId,
        String unitId,
        String unitName,
        String severity,
        boolean active,
        List<AlertErrorDTO> errors,
        String timestamp
) {
    /**
     * Создаёт активный алёрт (ошибка появилась).
     */
    @Contract("_, _, _, _, _, _ -> new")
    public static @NonNull AlertMessageDTO active(
            String workshopId,
            String unitId,
            String unitName,
            String severity,
            List<AlertErrorDTO> errors,
            String timestamp
    ) {
        return new AlertMessageDTO("ALERT", workshopId, unitId, unitName, severity, true, errors, timestamp);
    }

    /**
     * Создаёт resolved-алёрт (ошибка устранена) на основе текущей записи.
     *
     * @param resolvedAt ISO-8601 метка времени устранения
     */
    @Contract("_ -> new")
    public @NonNull AlertMessageDTO resolved(String resolvedAt) {
        return new AlertMessageDTO("ALERT", workshopId, unitId, unitName, severity, false, List.of(), resolvedAt);
    }
}
