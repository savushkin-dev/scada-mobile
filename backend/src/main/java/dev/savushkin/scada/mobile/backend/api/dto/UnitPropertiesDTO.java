package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * API DTO для свойств модуля, открытых через REST API.
 * <p>
 * Это упрощённый вид свойств модуля для клиентов API.
 * Может быть настроен независимо от внутренней доменной модели.
 */
@Schema(description = "Свойства unit (команды, параметры, настройки линии)")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitPropertiesDTO(
        @Schema(description = "Код команды для управления unit", example = "128")
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
}
