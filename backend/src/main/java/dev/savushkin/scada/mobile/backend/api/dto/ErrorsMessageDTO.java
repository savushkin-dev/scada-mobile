package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * WebSocket-сообщение типа {@code ERRORS} — вкладка «Журнал».
 *
 * <p>Отправляется клиенту по каналу {@code /ws/unit/{unitId}} при изменении
 * набора активных ошибок на устройствах. Содержит флаги ошибок из устройства
 * {@code scada}. Количество активных ошибок ({@code value = "1"}) используется
 * клиентом для отображения бейджа на вкладке «Журнал».
 *
 * <pre>
 * {
 *   "type": "ERRORS",
 *   "unitId": "trepko2",
 *   "timestamp": "2026-03-01T10:23:45",
 *   "payload": {
 *     "deviceErrors": [
 *       { "objectName": "Dev041", "propertyDesc": "Dev041Dublicate", "value": "0",
 *         "description": "Одинаковые коды маркировки" }
 *     ],
 *     "logs": []
 *   }
 * }
 * </pre>
 *
 * @param type      всегда {@code "ERRORS"}
 * @param unitId    идентификатор аппарата
 * @param timestamp ISO-8601 UTC момент формирования
 * @param payload   активные флаги ошибок
 */
public record ErrorsMessageDTO(
        String type,
        String unitId,
        String timestamp,
        Payload payload
) {

    @Contract("_, _, _ -> new")
    public static @NonNull ErrorsMessageDTO of(String unitId, String timestamp, Payload payload) {
        return new ErrorsMessageDTO("ERRORS", unitId, timestamp, payload);
    }

    /**
     * Ошибки и журнал событий.
     *
     * @param deviceErrors флаги активных ошибок от устройства {@code scada}
     * @param logs         записи журнала событий (всегда пустой список:
     *                     журнал хранится в SQLite на стороне PrintSrv)
     */
    public record Payload(List<DeviceErrorFlag> deviceErrors, List<Object> logs) {}

    /**
     * Один флаг ошибки устройства из снапшота {@code scada}.
     *
     * @param objectName   идентификатор устройства-источника (例 {@code "Dev041"})
     * @param propertyDesc ключ свойства из {@code scada} (例 {@code "Dev041Dublicate"})
     * @param value        значение флага ({@code "1"} — активна, {@code "0"} — нет)
     * @param description  человекочитаемое описание ошибки (例 {@code "Одинаковые коды маркировки"})
     */
    public record DeviceErrorFlag(String objectName, String propertyDesc, String value,
                                  String description) {}
}
