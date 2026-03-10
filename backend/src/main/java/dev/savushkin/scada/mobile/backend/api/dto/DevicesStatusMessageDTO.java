package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * WebSocket-сообщение типа {@code DEVICES_STATUS} — вкладка «Устройства».
 *
 * <p>Отправляется клиенту по каналу {@code /ws/unit/{unitId}} при изменении
 * состояния любого устройства или счётчиков камер. Устройства адресуются
 * по именам из ответа {@code GET .../devices/topology}.
 *
 * <pre>
 * {
 *   "type": "DEVICES_STATUS",
 *   "unitId": "trepko2",
 *   "timestamp": "2026-03-01T10:23:45",
 *   "payload": {
 *     "printers": [...],
 *     "aggregationCams": [...],
 *     "aggregationBoxCams": [...],
 *     "checkerCams": [...]
 *   }
 * }
 * </pre>
 *
 * @param type      всегда {@code "DEVICES_STATUS"}
 * @param unitId    идентификатор аппарата
 * @param timestamp ISO-8601 UTC момент формирования
 * @param payload   сгруппированные статусы устройств
 */
public record DevicesStatusMessageDTO(
        String type,
        String unitId,
        String timestamp,
        Payload payload
) {

    @Contract("_, _, _ -> new")
    public static @NonNull DevicesStatusMessageDTO of(String unitId, String timestamp, Payload payload) {
        return new DevicesStatusMessageDTO("DEVICES_STATUS", unitId, timestamp, payload);
    }

    /**
     * Статусы устройств, сгруппированных по функции.
     *
     * @param printers           статусы принтеров маркировки
     * @param aggregationCams    статусы камер агрегации
     * @param aggregationBoxCams статусы камер агрегации коробки
     * @param checkerCams        статусы камер проверки
     */
    public record Payload(
            List<PrinterStatus> printers,
            List<CameraStatus> aggregationCams,
            List<CameraStatus> aggregationBoxCams,
            List<CameraStatus> checkerCams
    ) {}

    /**
     * Статус одного принтера.
     *
     * @param deviceName имя устройства (例 {@code "Printer11"})
     * @param state      состояние: {@code "1"} — работа, {@code "0"} — стоп
     * @param error      код ошибки ({@code "0"} — нет ошибки)
     * @param batch      текущая позиция (маркировка | партия | дата)
     */
    public record PrinterStatus(
            String deviceName,
            @Nullable String state,
            @Nullable String error,
            @Nullable String batch
    ) {}

    /**
     * Статус одной камеры.
     *
     * @param deviceName имя устройства (例 {@code "CamAgregation"})
     * @param read       количество успешно считанных кодов
     * @param unread     количество несчитанных / ошибочных кодов
     * @param state      состояние (счётчик рабочего времени из Dev0xxWork)
     * @param error      код ошибки ({@code "0"} — нет ошибки)
     */
    public record CameraStatus(
            String deviceName,
            @Nullable String read,
            @Nullable String unread,
            @Nullable String state,
            @Nullable String error
    ) {}
}
