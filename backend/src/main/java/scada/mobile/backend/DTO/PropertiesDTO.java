package scada.mobile.backend.DTO;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class PropertiesDTO {
    private final int command;
    private final int message;
    private final String error;
    private final String errorMessage;
    private final String curItem;
    private final String batchId;
    private final String cmdSuccess;
    private final String ST;

    @JsonCreator
    public PropertiesDTO(
            @JsonProperty("command") int command,
            @JsonProperty("message") int message,
            @JsonProperty("Error") String error,
            @JsonProperty("ErrorMessage") String errorMessage,
            @JsonProperty("CurItem") String curItem,
            @JsonProperty("batchId") String batchId,
            @JsonProperty("cmdsuccess") String cmdSuccess,
            @JsonProperty("ST") String ST) {
        this.command = command;
        this.message = message;
        this.error = error;
        this.errorMessage = errorMessage;
        this.curItem = curItem;
        this.batchId = batchId;
        this.cmdSuccess = cmdSuccess;
        this.ST = ST;
    }

    public int getCommand() {
        return command;
    }

    public int getMessage() {
        return message;
    }

    public String getError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCurItem() {
        return curItem;
    }

    public String getBatchId() {
        return batchId;
    }

    public String getCmdSuccess() {
        return cmdSuccess;
    }

    public String getST() {
        return ST;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PropertiesDTO that)) return false;
        return command == that.command && message == that.message && Objects.equals(error, that.error) && Objects.equals(errorMessage, that.errorMessage) && Objects.equals(curItem, that.curItem) && Objects.equals(batchId, that.batchId) && Objects.equals(cmdSuccess, that.cmdSuccess) && Objects.equals(ST, that.ST);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, message, error, errorMessage, curItem, batchId, cmdSuccess, ST);
    }

    @Override
    public String toString() {
        return "PropertiesDTO{" +
                "command=" + command +
                ", message=" + message +
                ", error='" + error + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", curItem='" + curItem + '\'' +
                ", batchId='" + batchId + '\'' +
                ", cmdSuccess='" + cmdSuccess + '\'' +
                ", ST='" + ST + '\'' +
                '}';
    }
}
