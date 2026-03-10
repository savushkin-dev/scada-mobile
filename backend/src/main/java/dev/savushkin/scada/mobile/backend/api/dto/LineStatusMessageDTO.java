package dev.savushkin.scada.mobile.backend.api.dto;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * WebSocket-сообщение типа {@code LINE_STATUS} — вкладка «Партия».
 *
 * <p>Отправляется клиенту по каналу {@code /ws/unit/{unitId}} при изменении
 * состояния партии или статуса линии. Содержит данные вкладки «Партия» на
 * экране деталей аппарата.
 *
 * <pre>
 * {
 *   "type": "LINE_STATUS",
 *   "unitId": "trepko2",
 *   "timestamp": "2026-03-01T10:23:45",
 *   "payload": { ... }
 * }
 * </pre>
 *
 * <p>Источник полей: устройства {@code Line}, первый принтер, {@code BatchQueue}.
 *
 * @param type      всегда {@code "LINE_STATUS"}
 * @param unitId    идентификатор аппарата
 * @param timestamp ISO-8601 UTC момент формирования
 * @param payload   данные партии и состояния линии
 */
public record LineStatusMessageDTO(
        String type,
        String unitId,
        String timestamp,
        Payload payload
) {

    @Contract("_, _, _ -> new")
    public static @NonNull LineStatusMessageDTO of(String unitId, String timestamp, Payload payload) {
        return new LineStatusMessageDTO("LINE_STATUS", unitId, timestamp, payload);
    }

    /**
     * Поля вкладки «Партия».
     *
     * @param lineName        название линии (displayName из конфига)
     * @param lineState       состояние линии: {@code "1"} — работа, {@code "0"} — стоп
     * @param shortCode       краткий код продукта (kmc)
     * @param description     описание продукта
     * @param ean13           штрихкод EAN-13
     * @param batchNumber     номер партии
     * @param dateProduced    дата выработки
     * @param datePacking     дата фасовки
     * @param dateExpiration  дата годности
     * @param initialCounter  начальный счётчик маркировок
     * @param site            площадка / место на линии
     * @param itf             код ITF-14
     * @param capacity        ёмкость (единиц в коробе)
     * @param boxCount        количество коробок
     * @param packageCount    количество упаковок
     * @param freeze          заморозка партии
     * @param region          код региона
     * @param design          ID дизайна этикетки
     * @param printDM         печать DataMatrix ({@code "1"}/{@code "0"})
     */
    public record Payload(
            @Nullable String lineName,
            @Nullable String lineState,
            @Nullable String shortCode,
            @Nullable String description,
            @Nullable String ean13,
            @Nullable String batchNumber,
            @Nullable String dateProduced,
            @Nullable String datePacking,
            @Nullable String dateExpiration,
            @Nullable String initialCounter,
            @Nullable String site,
            @Nullable String itf,
            @Nullable String capacity,
            @Nullable String boxCount,
            @Nullable String packageCount,
            @Nullable String freeze,
            @Nullable String region,
            @Nullable String design,
            @Nullable String printDM
    ) {}
}
