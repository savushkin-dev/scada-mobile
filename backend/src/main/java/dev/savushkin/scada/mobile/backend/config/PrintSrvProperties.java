package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Типизированная конфигурация PrintSrv — единый источник правды о топологии линий.
 *
 * <p>Заменяет разрозненные {@code @Value("${printsrv.*}")} по всему коду.
 * Все существующие бины, читавшие отдельные поля через {@code @Value}, могут
 * постепенно переходить на инжекцию этого бина.
 *
 * <p>Активируется через {@link PrintSrvInfrastructureConfig}.
 */
@ConfigurationProperties(prefix = "printsrv")
public class PrintSrvProperties {

    private List<WorkshopProperties> workshops = new ArrayList<>();
    private List<InstanceProperties> instances = new ArrayList<>();
    private PollingProperties polling = new PollingProperties();
    private SocketProperties socket = new SocketProperties();

    // ─── getters / setters ────────────────────────────────────────────────────

    public List<WorkshopProperties> getWorkshops() { return workshops; }
    public void setWorkshops(List<WorkshopProperties> workshops) { this.workshops = workshops; }

    public List<InstanceProperties> getInstances() { return instances; }
    public void setInstances(List<InstanceProperties> instances) { this.instances = instances; }

    public PollingProperties getPolling() { return polling; }
    public void setPolling(PollingProperties polling) { this.polling = polling; }

    public SocketProperties getSocket() { return socket; }
    public void setSocket(SocketProperties socket) { this.socket = socket; }

    // ─── Nested: один PrintSrv-инстанс (физическая машина на линии) ──────────

    public static class InstanceProperties {
        /** Уникальный логический идентификатор: trepko1, hassia2, … */
        private String id;
        /** TCP-адрес хоста (в prod переопределяется через env-переменную). */
        private String host;
        /** TCP-порт PrintSrv для данного инстанса. */
        private int port;
        /** Человекочитаемое название для UI. */
        private String displayName;
        /** Ссылка на ID цеха из workshops[]. */
        private String workshopId;
        /**
         * Имена устройств PrintSrv для данного инстанса.
         */
        private DeviceNamesProperties devices = new DeviceNamesProperties();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getWorkshopId() { return workshopId; }
        public void setWorkshopId(String workshopId) { this.workshopId = workshopId;
        }

        public DeviceNamesProperties getDevices() {
            if (devices == null) {
                devices = new DeviceNamesProperties();
            }
            return devices;
        }

        public void setDevices(DeviceNamesProperties devices) {
            this.devices = devices;
        }

        public List<String> getAllDeviceNames() {
            return getDevices().getAllDeviceNames();
        }

        @Override
        public String toString() {
            return "InstanceProperties{id='%s', host='%s', port=%d, workshopId='%s', devices=%s}"
                    .formatted(id, host, port, workshopId, getAllDeviceNames());
        }
    }

    // ─── Nested: имена устройств инстанса ───────────────────────────────────

    public static class DeviceNamesProperties {
        private String line = "Line";
        private String scada = "scada";
        private String batchQueue = "BatchQueue";
        private List<String> printers = new ArrayList<>(List.of("Printer11"));
        private List<String> aggregationCams = new ArrayList<>(List.of("CamAgregation"));
        private List<String> aggregationBoxCams = new ArrayList<>(List.of("CamAgregationBox"));
        private List<String> checkerCams = new ArrayList<>(List.of("CamChecker"));

        private static void addAllIfPresent(LinkedHashSet<String> target, List<String> values) {
            for (String value : values) {
                addIfPresent(target, value);
            }
        }

        private static void addIfPresent(LinkedHashSet<String> target, String value) {
            if (value == null) {
                return;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }

        public String getLine() {
            return line;
        }

        public void setLine(String line) {
            this.line = line;
        }

        public String getScada() {
            return scada;
        }

        public void setScada(String scada) {
            this.scada = scada;
        }

        public String getBatchQueue() {
            return batchQueue;
        }

        public void setBatchQueue(String batchQueue) {
            this.batchQueue = batchQueue;
        }

        public List<String> getPrinters() {
            if (printers == null) {
                printers = new ArrayList<>();
            }
            return printers;
        }

        public void setPrinters(List<String> printers) {
            this.printers = printers;
        }

        public List<String> getAggregationCams() {
            if (aggregationCams == null) {
                aggregationCams = new ArrayList<>();
            }
            return aggregationCams;
        }

        public void setAggregationCams(List<String> aggregationCams) {
            this.aggregationCams = aggregationCams;
        }

        public List<String> getAggregationBoxCams() {
            if (aggregationBoxCams == null) {
                aggregationBoxCams = new ArrayList<>();
            }
            return aggregationBoxCams;
        }

        public void setAggregationBoxCams(List<String> aggregationBoxCams) {
            this.aggregationBoxCams = aggregationBoxCams;
        }

        public List<String> getCheckerCams() {
            if (checkerCams == null) {
                checkerCams = new ArrayList<>();
            }
            return checkerCams;
        }

        public void setCheckerCams(List<String> checkerCams) {
            this.checkerCams = checkerCams;
        }

        public List<String> getAllDeviceNames() {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            addIfPresent(names, line);
            addIfPresent(names, scada);
            addIfPresent(names, batchQueue);
            addAllIfPresent(names, getPrinters());
            addAllIfPresent(names, getAggregationCams());
            addAllIfPresent(names, getAggregationBoxCams());
            addAllIfPresent(names, getCheckerCams());
            return List.copyOf(names);
        }
    }

    // ─── Nested: цех ─────────────────────────────────────────────────────────

    public static class WorkshopProperties {
        private String id;
        private String displayName;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    // ─── Nested: polling ─────────────────────────────────────────────────────

    public static class PollingProperties {
        /**
         * Задержка между polling-проходами каждого instance worker в мс.
         */
        private long fixedDelayMs = 5000;

        public long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    }

    // ─── Nested: socket ──────────────────────────────────────────────────────

    public static class SocketProperties {
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 5000;

        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }
}
