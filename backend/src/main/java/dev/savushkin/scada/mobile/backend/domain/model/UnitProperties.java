package dev.savushkin.scada.mobile.backend.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Domain model representing properties of a SCADA unit.
 * <p>
 * This is a pure domain model that captures various operational properties of a unit.
 * Unlike the DTO version, this model uses non-null types where the business logic
 * requires them, and properly typed Optional for truly optional values.
 * <p>
 * This class is immutable and thread-safe.
 */
public final class UnitProperties {
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

    private UnitProperties(Builder builder) {
        this.command = builder.command;
        this.message = builder.message;
        this.error = builder.error;
        this.errorMessage = builder.errorMessage;
        this.cmdSuccess = builder.cmdSuccess;
        this.st = builder.st;
        this.batchId = builder.batchId;
        this.curItem = builder.curItem;
        this.batchIdCodesQueue = builder.batchIdCodesQueue;
        this.setBatchId = builder.setBatchId;
        this.devChangeBatch = builder.devChangeBatch;
        this.devsChangeBatchIdQueueControl = builder.devsChangeBatchIdQueueControl;
        this.devType = builder.devType;
        this.lineId = builder.lineId;
        this.onChangeBatchPrinters = builder.onChangeBatchPrinters;
        this.level1Printers = builder.level1Printers;
        this.level2Printers = builder.level2Printers;
        this.onChangeBatchCams = builder.onChangeBatchCams;
        this.level1Cams = builder.level1Cams;
        this.level2Cams = builder.level2Cams;
        this.signalCams = builder.signalCams;
        this.lineDevices = builder.lineDevices;
        this.enableErrors = builder.enableErrors;
    }

    public Optional<Integer> getCommand() {
        return Optional.ofNullable(command);
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    public Optional<String> getError() {
        return Optional.ofNullable(error);
    }

    public Optional<String> getErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public Optional<String> getCmdSuccess() {
        return Optional.ofNullable(cmdSuccess);
    }

    public Optional<String> getSt() {
        return Optional.ofNullable(st);
    }

    public Optional<String> getBatchId() {
        return Optional.ofNullable(batchId);
    }

    public Optional<String> getCurItem() {
        return Optional.ofNullable(curItem);
    }

    public Optional<String> getBatchIdCodesQueue() {
        return Optional.ofNullable(batchIdCodesQueue);
    }

    public Optional<String> getSetBatchId() {
        return Optional.ofNullable(setBatchId);
    }

    public Optional<String> getDevChangeBatch() {
        return Optional.ofNullable(devChangeBatch);
    }

    public Optional<String> getDevsChangeBatchIdQueueControl() {
        return Optional.ofNullable(devsChangeBatchIdQueueControl);
    }

    public Optional<String> getDevType() {
        return Optional.ofNullable(devType);
    }

    public Optional<String> getLineId() {
        return Optional.ofNullable(lineId);
    }

    public Optional<String> getOnChangeBatchPrinters() {
        return Optional.ofNullable(onChangeBatchPrinters);
    }

    public Optional<String> getLevel1Printers() {
        return Optional.ofNullable(level1Printers);
    }

    public Optional<String> getLevel2Printers() {
        return Optional.ofNullable(level2Printers);
    }

    public Optional<String> getOnChangeBatchCams() {
        return Optional.ofNullable(onChangeBatchCams);
    }

    public Optional<String> getLevel1Cams() {
        return Optional.ofNullable(level1Cams);
    }

    public Optional<String> getLevel2Cams() {
        return Optional.ofNullable(level2Cams);
    }

    public Optional<String> getSignalCams() {
        return Optional.ofNullable(signalCams);
    }

    public Optional<String> getLineDevices() {
        return Optional.ofNullable(lineDevices);
    }

    public Optional<String> getEnableErrors() {
        return Optional.ofNullable(enableErrors);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitProperties that = (UnitProperties) o;
        return Objects.equals(command, that.command)
                && Objects.equals(message, that.message)
                && Objects.equals(error, that.error)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(cmdSuccess, that.cmdSuccess)
                && Objects.equals(st, that.st)
                && Objects.equals(batchId, that.batchId)
                && Objects.equals(curItem, that.curItem)
                && Objects.equals(batchIdCodesQueue, that.batchIdCodesQueue)
                && Objects.equals(setBatchId, that.setBatchId)
                && Objects.equals(devChangeBatch, that.devChangeBatch)
                && Objects.equals(devsChangeBatchIdQueueControl, that.devsChangeBatchIdQueueControl)
                && Objects.equals(devType, that.devType)
                && Objects.equals(lineId, that.lineId)
                && Objects.equals(onChangeBatchPrinters, that.onChangeBatchPrinters)
                && Objects.equals(level1Printers, that.level1Printers)
                && Objects.equals(level2Printers, that.level2Printers)
                && Objects.equals(onChangeBatchCams, that.onChangeBatchCams)
                && Objects.equals(level1Cams, that.level1Cams)
                && Objects.equals(level2Cams, that.level2Cams)
                && Objects.equals(signalCams, that.signalCams)
                && Objects.equals(lineDevices, that.lineDevices)
                && Objects.equals(enableErrors, that.enableErrors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, message, error, errorMessage, cmdSuccess, st, batchId,
                curItem, batchIdCodesQueue, setBatchId, devChangeBatch, devsChangeBatchIdQueueControl,
                devType, lineId, onChangeBatchPrinters, level1Printers, level2Printers,
                onChangeBatchCams, level1Cams, level2Cams, signalCams, lineDevices, enableErrors);
    }

    @Override
    public String toString() {
        return "UnitProperties{" +
                "command=" + command +
                ", message='" + message + '\'' +
                ", error='" + error + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", cmdSuccess='" + cmdSuccess + '\'' +
                ", st='" + st + '\'' +
                ", batchId='" + batchId + '\'' +
                ", curItem='" + curItem + '\'' +
                ", batchIdCodesQueue='" + batchIdCodesQueue + '\'' +
                ", setBatchId='" + setBatchId + '\'' +
                ", devChangeBatch='" + devChangeBatch + '\'' +
                ", devsChangeBatchIdQueueControl='" + devsChangeBatchIdQueueControl + '\'' +
                ", devType='" + devType + '\'' +
                ", lineId='" + lineId + '\'' +
                ", onChangeBatchPrinters='" + onChangeBatchPrinters + '\'' +
                ", level1Printers='" + level1Printers + '\'' +
                ", level2Printers='" + level2Printers + '\'' +
                ", onChangeBatchCams='" + onChangeBatchCams + '\'' +
                ", level1Cams='" + level1Cams + '\'' +
                ", level2Cams='" + level2Cams + '\'' +
                ", signalCams='" + signalCams + '\'' +
                ", lineDevices='" + lineDevices + '\'' +
                ", enableErrors='" + enableErrors + '\'' +
                '}';
    }

    /**
     * Creates a builder for constructing UnitProperties instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
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

        private Builder() {
        }

        public Builder command(Integer command) {
            this.command = command;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder cmdSuccess(String cmdSuccess) {
            this.cmdSuccess = cmdSuccess;
            return this;
        }

        public Builder st(String st) {
            this.st = st;
            return this;
        }

        public Builder batchId(String batchId) {
            this.batchId = batchId;
            return this;
        }

        public Builder curItem(String curItem) {
            this.curItem = curItem;
            return this;
        }

        public Builder batchIdCodesQueue(String batchIdCodesQueue) {
            this.batchIdCodesQueue = batchIdCodesQueue;
            return this;
        }

        public Builder setBatchId(String setBatchId) {
            this.setBatchId = setBatchId;
            return this;
        }

        public Builder devChangeBatch(String devChangeBatch) {
            this.devChangeBatch = devChangeBatch;
            return this;
        }

        public Builder devsChangeBatchIdQueueControl(String devsChangeBatchIdQueueControl) {
            this.devsChangeBatchIdQueueControl = devsChangeBatchIdQueueControl;
            return this;
        }

        public Builder devType(String devType) {
            this.devType = devType;
            return this;
        }

        public Builder lineId(String lineId) {
            this.lineId = lineId;
            return this;
        }

        public Builder onChangeBatchPrinters(String onChangeBatchPrinters) {
            this.onChangeBatchPrinters = onChangeBatchPrinters;
            return this;
        }

        public Builder level1Printers(String level1Printers) {
            this.level1Printers = level1Printers;
            return this;
        }

        public Builder level2Printers(String level2Printers) {
            this.level2Printers = level2Printers;
            return this;
        }

        public Builder onChangeBatchCams(String onChangeBatchCams) {
            this.onChangeBatchCams = onChangeBatchCams;
            return this;
        }

        public Builder level1Cams(String level1Cams) {
            this.level1Cams = level1Cams;
            return this;
        }

        public Builder level2Cams(String level2Cams) {
            this.level2Cams = level2Cams;
            return this;
        }

        public Builder signalCams(String signalCams) {
            this.signalCams = signalCams;
            return this;
        }

        public Builder lineDevices(String lineDevices) {
            this.lineDevices = lineDevices;
            return this;
        }

        public Builder enableErrors(String enableErrors) {
            this.enableErrors = enableErrors;
            return this;
        }

        public UnitProperties build() {
            return new UnitProperties(this);
        }
    }
}
