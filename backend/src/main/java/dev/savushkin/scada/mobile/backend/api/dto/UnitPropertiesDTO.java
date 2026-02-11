package dev.savushkin.scada.mobile.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * API DTO for unit properties exposed through the REST API.
 * <p>
 * This is a simplified view of unit properties for API clients.
 * It can be customized independently from the internal domain model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnitPropertiesDTO(
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
