package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
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
    private RetryProperties retry = new RetryProperties();
    private SocketProperties socket = new SocketProperties();

    // ─── getters / setters ────────────────────────────────────────────────────

    public List<WorkshopProperties> getWorkshops() { return workshops; }
    public void setWorkshops(List<WorkshopProperties> workshops) { this.workshops = workshops; }

    public List<InstanceProperties> getInstances() { return instances; }
    public void setInstances(List<InstanceProperties> instances) { this.instances = instances; }

    public PollingProperties getPolling() { return polling; }
    public void setPolling(PollingProperties polling) { this.polling = polling; }

    public RetryProperties getRetry() { return retry; }
    public void setRetry(RetryProperties retry) { this.retry = retry; }

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

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getWorkshopId() { return workshopId; }
        public void setWorkshopId(String workshopId) { this.workshopId = workshopId; }

        @Override
        public String toString() {
            return "InstanceProperties{id='%s', host='%s', port=%d, workshopId='%s'}"
                    .formatted(id, host, port, workshopId);
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
        /** fixedDelay между scan cycle в мс. Переопределяется в профилях. */
        private long fixedDelayMs = 5000;

        public long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    }

    // ─── Nested: retry ───────────────────────────────────────────────────────

    public static class RetryProperties {
        private int maxAttempts = 5;
        private int initialDelayMs = 200;
        private int maxDelayMs = 5000;
        private long recoveryCheckIntervalMs = 60_000;

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public int getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(int initialDelayMs) { this.initialDelayMs = initialDelayMs; }

        public int getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(int maxDelayMs) { this.maxDelayMs = maxDelayMs; }

        public long getRecoveryCheckIntervalMs() { return recoveryCheckIntervalMs; }
        public void setRecoveryCheckIntervalMs(long recoveryCheckIntervalMs) {
            this.recoveryCheckIntervalMs = recoveryCheckIntervalMs;
        }
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
