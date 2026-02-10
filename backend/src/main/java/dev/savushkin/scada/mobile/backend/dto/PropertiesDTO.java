package dev.savushkin.scada.mobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO для свойств юнита.
 * Поля могут отсутствовать в некоторых ответах (например, SetUnitVars возвращает только изменённые поля).
 * <p>
 * ВАЖНО: command - Integer (целое число), не String!
 * <p>
 * Все поля nullable, так как PrintSrv возвращает только изменённые значения.
 *
 * @param command               Команда (целое число)
 * @param message               Сообщение
 * @param error                 Код ошибки
 * @param errorMessage          Описание ошибки
 * @param cmdSuccess            Статус успешности команды
 * @param st                    Статус ST
 * @param batchId               ID партии
 * @param curItem               Текущий элемент
 * @param batchIdCodesQueue     Очередь кодов партий
 * @param setBatchId            Установка ID партии
 * @param devChangeBatch        Устройство изменения партии
 * @param devsChangeBatchIdQueueControl Контроль очереди изменения партий
 * @param devType               Тип устройства
 * @param lineId                ID линии
 * @param onChangeBatchPrinters Принтеры при смене партии
 * @param level1Printers        Принтеры уровня 1
 * @param level2Printers        Принтеры уровня 2
 * @param onChangeBatchCams     Камеры при смене партии
 * @param level1Cams            Камеры уровня 1
 * @param level2Cams            Камеры уровня 2
 * @param signalCams            Сигнальные камеры
 * @param lineDevices           Устройства линии
 * @param enableErrors          Включение ошибок
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertiesDTO(
        @JsonProperty("command") Integer command,
        @JsonProperty("message") String message,
        @JsonProperty("Error") String error,
        @JsonProperty("ErrorMessage") String errorMessage,
        @JsonProperty("cmdsuccess") String cmdSuccess,
        @JsonProperty("ST") String st,
        @JsonProperty("batchId") String batchId,
        @JsonProperty("CurItem") String curItem,
        @JsonProperty("batchIdCodesQueue") String batchIdCodesQueue,
        @JsonProperty("setBatchID") String setBatchId,
        @JsonProperty("devChangeBatch") String devChangeBatch,
        @JsonProperty("devsChangeBatchIDQueueControl") String devsChangeBatchIdQueueControl,
        @JsonProperty("devType") String devType,
        @JsonProperty("LineID") String lineId,
        @JsonProperty("OnChangeBatchPrinters") String onChangeBatchPrinters,
        @JsonProperty("Level1Printers") String level1Printers,
        @JsonProperty("Level2Printers") String level2Printers,
        @JsonProperty("OnChangeBatchCams") String onChangeBatchCams,
        @JsonProperty("Level1Cams") String level1Cams,
        @JsonProperty("Level2Cams") String level2Cams,
        @JsonProperty("SignalCams") String signalCams,
        @JsonProperty("LineDevices") String lineDevices,
        @JsonProperty("enableErrors") String enableErrors
) {
    /**
     * Factory method для создания PropertiesDTO только с command полем.
     * Остальные поля устанавливаются в null.
     *
     * @param commandValue значение команды
     * @return PropertiesDTO с заполненным только command полем
     */
    public static PropertiesDTO withCommand(Integer commandValue) {
        return new PropertiesDTO(
                commandValue,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
    }
}
