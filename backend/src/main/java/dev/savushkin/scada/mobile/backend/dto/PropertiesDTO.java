package dev.savushkin.scada.mobile.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * DTO для свойств юнита.
 * Все поля строковые для совместимости с JSON-форматом PrintSrv.
 * Поля могут отсутствовать в некоторых ответах (например, SetUnitVars возвращает только изменённые поля).
 *
 * @param command               Основные поля состояния
 * @param error                 Поля ошибок
 * @param st                    Поля статуса ST - статус
 * @param batchId               Поля партии
 * @param devType               Поля устройства
 * @param onChangeBatchPrinters Поля принтеров
 * @param onChangeBatchCams     Поля камер
 * @param lineDevices           Общие устройства
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PropertiesDTO(String command, String message, String error, String errorMessage, String cmdSuccess,
                            String st, String batchId, String curItem, String batchIdCodesQueue, String setBatchId,
                            String devChangeBatch, String devsChangeBatchIdQueueControl, String devType, String lineId,
                            String onChangeBatchPrinters, String level1Printers, String level2Printers,
                            String onChangeBatchCams, String level1Cams, String level2Cams, String signalCams,
                            String lineDevices, String enableErrors) {
    @JsonCreator
    public PropertiesDTO(
            @JsonProperty("command") String command,
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
        this.command = command;
        this.message = message;
        this.error = error;
        this.errorMessage = errorMessage;
        this.cmdSuccess = cmdSuccess;
        this.st = st;
        this.batchId = batchId;
        this.curItem = curItem;
        this.batchIdCodesQueue = batchIdCodesQueue;
        this.setBatchId = setBatchId;
        this.devChangeBatch = devChangeBatch;
        this.devsChangeBatchIdQueueControl = devsChangeBatchIdQueueControl;
        this.devType = devType;
        this.lineId = lineId;
        this.onChangeBatchPrinters = onChangeBatchPrinters;
        this.level1Printers = level1Printers;
        this.level2Printers = level2Printers;
        this.onChangeBatchCams = onChangeBatchCams;
        this.level1Cams = level1Cams;
        this.level2Cams = level2Cams;
        this.signalCams = signalCams;
        this.lineDevices = lineDevices;
        this.enableErrors = enableErrors;
    }

    // Getters

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PropertiesDTO that)) return false;
        return Objects.equals(command, that.command)
                && Objects.equals(message, that.message)
                && Objects.equals(error, that.error)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(cmdSuccess, that.cmdSuccess)
                && Objects.equals(st, that.st)
                && Objects.equals(batchId, that.batchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, message, error, errorMessage, cmdSuccess, st, batchId);
    }

    @Override
    public String toString() {
        return "PropertiesDTO{" +
                "command='" + command + '\'' +
                ", message='" + message + '\'' +
                ", error='" + error + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", cmdSuccess='" + cmdSuccess + '\'' +
                ", st='" + st + '\'' +
                ", ...}";
    }
}
