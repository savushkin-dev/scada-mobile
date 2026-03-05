package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * WebSocket-сообщение типа {@code ALERT_SNAPSHOT} — начальный снапшот всех
 * активных алёртов, отправляемый клиенту сразу после установки соединения.
 * <p>
 * Клиент использует этот снапшот как стартовое состояние для {@code Map<unitId, Alert>}
 * в глобальном стейте, чтобы корректно отображать {@code problemUnits} на дашборде
 * и выделять карточки с ошибками ещё до получения первого дельта-сообщения {@code ALERT}.
 *
 * <pre>
 * {
 *   "type": "ALERT_SNAPSHOT",
 *   "payload": [
 *     {
 *       "type": "ALERT", "workshopId": "apparatniy", "unitId": "hassia2",
 *       "unitName": "Линия розлива ПЭТ №2", "severity": "Critical",
 *       "active": true,
 *       "errors": [{"device":"Line","code":0,"message":"Ошибка"}],
 *       "timestamp": "2026-03-05T10:23:45"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @param type    Всегда {@code "ALERT_SNAPSHOT"}.
 * @param payload Список активных алёртов на момент подключения. Может быть пустым.
 */
public record AlertSnapshotMessageDTO(
        String type,
        List<AlertMessageDTO> payload
) {
    /**
     * Фабричный метод — тип всегда фиксирован.
     */
    @Contract("_ -> new")
    public static @NonNull AlertSnapshotMessageDTO of(List<AlertMessageDTO> alerts) {
        return new AlertSnapshotMessageDTO("ALERT_SNAPSHOT", alerts);
    }
}
