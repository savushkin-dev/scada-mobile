package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO для свойств юнита, возвращённых PrintSrv.
 *
 * <p>Именованные поля соответствуют Line-домену ({@code Error}, {@code ST},
 * {@code CurItem}, {@code Level1Printers}…). Все остальные ключи JSON-ответа —
 * camera-специфичные ({@code Total}, {@code Failed}, {@code kd}, {@code LastRead}
 * и др.) — автоматически собираются в {@link #rawProperties()} без потерь.
 *
 * <p>Класс иммутабелен и потокобезопасен. Jackson десериализует через Builder
 * ({@code @JsonDeserialize}); для ручной сборки (mock-клиент) используйте
 * {@link #builder()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(builder = PropertiesDTO.Builder.class)
public final class PropertiesDTO {

    private final Integer command;
    private final String message;
    private final String error;
    private final String errorMessage;
    private final String cmdSuccess;
    private final String st;
    private final String batchId;
    private final String curItem;
    private final String batchIdCodesQueue;
    private final String setBatchId;
    private final String devChangeBatch;
    private final String devsChangeBatchIdQueueControl;
    private final String devType;
    private final String lineId;
    private final String onChangeBatchPrinters;
    private final String level1Printers;
    private final String level2Printers;
    private final String onChangeBatchCams;
    private final String level1Cams;
    private final String level2Cams;
    private final String signalCams;
    private final String lineDevices;
    private final String enableErrors;

    /**
     * Все свойства, не покрытые именованными полями выше.
     * Для camera-устройств содержит {@code Total}, {@code Failed},
     * {@code Succeeded}, {@code kd}, {@code LastRead} и т.д.
     * Никогда не null — пустой Map, если лишних свойств нет.
     */
    private final Map<String, String> rawProperties;

    private PropertiesDTO(Builder b) {
        this.command = b.command;
        this.message = b.message;
        this.error = b.error;
        this.errorMessage = b.errorMessage;
        this.cmdSuccess = b.cmdSuccess;
        this.st = b.st;
        this.batchId = b.batchId;
        this.curItem = b.curItem;
        this.batchIdCodesQueue = b.batchIdCodesQueue;
        this.setBatchId = b.setBatchId;
        this.devChangeBatch = b.devChangeBatch;
        this.devsChangeBatchIdQueueControl = b.devsChangeBatchIdQueueControl;
        this.devType = b.devType;
        this.lineId = b.lineId;
        this.onChangeBatchPrinters = b.onChangeBatchPrinters;
        this.level1Printers = b.level1Printers;
        this.level2Printers = b.level2Printers;
        this.onChangeBatchCams = b.onChangeBatchCams;
        this.level1Cams = b.level1Cams;
        this.level2Cams = b.level2Cams;
        this.signalCams = b.signalCams;
        this.lineDevices = b.lineDevices;
        this.enableErrors = b.enableErrors;
        this.rawProperties = b.rawProperties.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(b.rawProperties));
    }

    /** Создаёт новый Builder для ручной сборки (mock-клиент, тесты). */
    public static Builder builder() {
        return new Builder();
    }

    // ── Accessors — имена совпадают с record-компонентами для обратной совместимости ──

    public Integer command()                        { return command; }
    public String  message()                        { return message; }
    public String  error()                          { return error; }
    public String  errorMessage()                   { return errorMessage; }
    public String  cmdSuccess()                     { return cmdSuccess; }
    public String  st()                             { return st; }
    public String  batchId()                        { return batchId; }
    public String  curItem()                        { return curItem; }
    public String  batchIdCodesQueue()              { return batchIdCodesQueue; }
    public String  setBatchId()                     { return setBatchId; }
    public String  devChangeBatch()                 { return devChangeBatch; }
    public String  devsChangeBatchIdQueueControl()  { return devsChangeBatchIdQueueControl; }
    public String  devType()                        { return devType; }
    public String  lineId()                         { return lineId; }
    public String  onChangeBatchPrinters()          { return onChangeBatchPrinters; }
    public String  level1Printers()                 { return level1Printers; }
    public String  level2Printers()                 { return level2Printers; }
    public String  onChangeBatchCams()              { return onChangeBatchCams; }
    public String  level1Cams()                     { return level1Cams; }
    public String  level2Cams()                     { return level2Cams; }
    public String  signalCams()                     { return signalCams; }
    public String  lineDevices()                    { return lineDevices; }
    public String  enableErrors()                   { return enableErrors; }

    /**
     * Свойства устройства, не покрытые именованными полями.
     * Актуально для camera-устройств: {@code Total}, {@code Failed}, {@code kd}, etc.
     */
    public Map<String, String> rawProperties()      { return rawProperties; }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Builder для {@link PropertiesDTO}.
     *
     * <p>Jackson использует его при десериализации TCP-JSON через
     * {@code @JsonDeserialize}. {@link #anyProperty(String, Object)} помечен
     * {@link JsonAnySetter} — все нераспознанные JSON-ключи попадают в
     * {@code rawProperties} (camera-поля, специфичные для устройства).
     *
     * <p>Для mock-клиента вызывается напрямую через {@link PropertiesDTO#builder()}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private Integer command;
        private String message;
        private String error;
        private String errorMessage;
        private String cmdSuccess;
        private String st;
        private String batchId;
        private String curItem;
        private String batchIdCodesQueue;
        private String setBatchId;
        private String devChangeBatch;
        private String devsChangeBatchIdQueueControl;
        private String devType;
        private String lineId;
        private String onChangeBatchPrinters;
        private String level1Printers;
        private String level2Printers;
        private String onChangeBatchCams;
        private String level1Cams;
        private String level2Cams;
        private String signalCams;
        private String lineDevices;
        private String enableErrors;
        private final Map<String, String> rawProperties = new LinkedHashMap<>();

        public Builder() {}

        @JsonProperty("command")
        public Builder command(Integer command)                                          { this.command = command; return this; }
        @JsonProperty("message")
        public Builder message(String message)                                          { this.message = message; return this; }
        @JsonProperty("Error")
        public Builder error(String error)                                              { this.error = error; return this; }
        @JsonProperty("ErrorMessage")
        public Builder errorMessage(String errorMessage)                                { this.errorMessage = errorMessage; return this; }
        @JsonProperty("cmdsuccess")
        public Builder cmdSuccess(String cmdSuccess)                                    { this.cmdSuccess = cmdSuccess; return this; }
        @JsonProperty("ST")
        public Builder st(String st)                                                    { this.st = st; return this; }
        @JsonProperty("batchId")
        @JsonAlias("batchid")
        public Builder batchId(String batchId)                                          { this.batchId = batchId; return this; }
        @JsonProperty("CurItem")
        @JsonAlias("curitem")
        public Builder curItem(String curItem)                                          { this.curItem = curItem; return this; }
        @JsonProperty("batchIdCodesQueue")
        public Builder batchIdCodesQueue(String batchIdCodesQueue)                      { this.batchIdCodesQueue = batchIdCodesQueue; return this; }
        @JsonProperty("setBatchID")
        public Builder setBatchId(String setBatchId)                                    { this.setBatchId = setBatchId; return this; }
        @JsonProperty("devChangeBatch")
        public Builder devChangeBatch(String devChangeBatch)                            { this.devChangeBatch = devChangeBatch; return this; }
        @JsonProperty("devsChangeBatchIDQueueControl")
        public Builder devsChangeBatchIdQueueControl(String devsChangeBatchIdQueueControl) { this.devsChangeBatchIdQueueControl = devsChangeBatchIdQueueControl; return this; }
        @JsonProperty("devType")
        public Builder devType(String devType)                                          { this.devType = devType; return this; }
        @JsonProperty("LineID")
        public Builder lineId(String lineId)                                            { this.lineId = lineId; return this; }
        @JsonProperty("OnChangeBatchPrinters")
        public Builder onChangeBatchPrinters(String onChangeBatchPrinters)              { this.onChangeBatchPrinters = onChangeBatchPrinters; return this; }
        @JsonProperty("Level1Printers")
        public Builder level1Printers(String level1Printers)                           { this.level1Printers = level1Printers; return this; }
        @JsonProperty("Level2Printers")
        public Builder level2Printers(String level2Printers)                           { this.level2Printers = level2Printers; return this; }
        @JsonProperty("OnChangeBatchCams")
        public Builder onChangeBatchCams(String onChangeBatchCams)                      { this.onChangeBatchCams = onChangeBatchCams; return this; }
        @JsonProperty("Level1Cams")
        public Builder level1Cams(String level1Cams)                                   { this.level1Cams = level1Cams; return this; }
        @JsonProperty("Level2Cams")
        public Builder level2Cams(String level2Cams)                                   { this.level2Cams = level2Cams; return this; }
        @JsonProperty("SignalCams")
        public Builder signalCams(String signalCams)                                    { this.signalCams = signalCams; return this; }
        @JsonProperty("LineDevices")
        public Builder lineDevices(String lineDevices)                                  { this.lineDevices = lineDevices; return this; }
        @JsonProperty("enableErrors")
        public Builder enableErrors(String enableErrors)                                { this.enableErrors = enableErrors; return this; }

        /**
         * Принимает все нераспознанные Jackson-ом JSON-ключи (camera-поля и прочие
         * device-специфичные свойства). Null-значения безопасно игнорируются.
         */
        @JsonAnySetter
        public Builder anyProperty(String key, Object value) {
            if (key != null && value != null) {
                rawProperties.put(key, value.toString());
            }
            return this;
        }

        /**
         * Добавляет все переданные пары в rawProperties (удобно для mock-клиента,
         * которому нужно передать полную карту свойств из XML-снапшота).
         * Не перезаписывает уже установленные пары — семантика merge.
         */
        public Builder rawProperties(Map<String, String> props) {
            if (props != null) {
                props.forEach(this::anyProperty);
            }
            return this;
        }

        public PropertiesDTO build() {
            return new PropertiesDTO(this);
        }
    }
}
