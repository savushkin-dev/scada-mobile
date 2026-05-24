package dev.savushkin.scada.mobile.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Типизированная конфигурация PrintSrv — только runtime-параметры.
 *
 * <p>Топология (цеха, инстансы, устройства) перенесена в БД
 * и читается через {@link dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository}.
 * Этот класс оставлен только для polling-интервалов и socket-таймаутов.
 *
 * <p>Активируется через {@link PrintSrvInfrastructureConfig}.
 */
@ConfigurationProperties(prefix = "printsrv")
public class PrintSrvProperties {

    private PollingProperties polling = new PollingProperties();
    private SocketProperties socket = new SocketProperties();

    // ─── getters / setters ────────────────────────────────────────────────────

    public PollingProperties getPolling() { return polling; }
    public void setPolling(PollingProperties polling) { this.polling = polling; }

    public SocketProperties getSocket() { return socket; }
    public void setSocket(SocketProperties socket) { this.socket = socket; }

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
