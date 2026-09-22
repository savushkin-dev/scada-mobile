package dev.savushkin.scada.mobile.backend.config;

import lombok.Getter;
import lombok.Setter;
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
@Setter
@Getter
@ConfigurationProperties(prefix = "printsrv")
public class PrintSrvProperties {

    private PollingProperties polling = new PollingProperties();
    private SocketProperties socket = new SocketProperties();
    private BatchEndProperties batchEnd = new BatchEndProperties();

    // ─── getters / setters ────────────────────────────────────────────────────

    // ─── Nested: polling ─────────────────────────────────────────────────────

    @Setter
    @Getter
    public static class PollingProperties {
        /**
         * Задержка между polling-проходами каждого instance worker в мс.
         */
        private long fixedDelayMs = 5000;

    }

    // ─── Nested: socket ──────────────────────────────────────────────────────

    @Setter
    @Getter
    public static class SocketProperties {
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 5000;

    }

    // ─── Nested: batch-end ───────────────────────────────────────────────────

    @Setter
    @Getter
    public static class BatchEndProperties {
        /**
         * Имя устройства-источника сигнала «последняя партия».
         */
        private String deviceName = "Line";

        /**
         * Имя свойства-счётчика «последней партии». Markserver инкрементирует
         * его при получении команды {@code LINE_CMD_FINISH_BATCH (113)}
         * (значение циклически меняется 1→100→1), поэтому срабатывание
         * детектируется по <em>изменению</em> значения между poll-циклами.
         */
        private String propertyName = "FinishBatch";

    }
}
