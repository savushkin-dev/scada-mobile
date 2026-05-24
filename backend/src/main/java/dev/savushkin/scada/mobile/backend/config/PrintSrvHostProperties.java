package dev.savushkin.scada.mobile.backend.config;

import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Типизированная конфигурация хостов и портов PrintSrv-инстансов.
 *
 * <p>Читает переменные окружения вида:
 * <pre>
 *   SCADA_MOBILE_PRINTSRV_{INSTANCE_ID}_HOST=192.168.1.10
 *   SCADA_MOBILE_PRINTSRV_{INSTANCE_ID}_PORT=9100
 * </pre>
 *
 * <p>Spring Boot автоматически маппит CamelCase-поля на snake_case env-переменные:
 * {@code printsrv.hosts.trepko1.host} → {@code PRINTSRV_HOSTS_TREPKO1_HOST}.
 * Наш префикс {@code scada.mobile} добавляется через {@code SPRING_APPLICATION_NAME}.
 *
 * <p>Активируется через {@link PrintSrvInfrastructureConfig}.
 */
@ConfigurationProperties(prefix = "scada.mobile.printsrv")
public class PrintSrvHostProperties {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvHostProperties.class);

    /**
     * Карта хостов/портов по instanceId.
     * Ключ — instanceId (например, "trepko1"), значение — host+port.
     */
    private Map<String, HostPort> hosts = new LinkedHashMap<>();

    public Map<String, HostPort> getHosts() {
        return hosts;
    }

    public void setHosts(Map<String, HostPort> hosts) {
        this.hosts = hosts != null ? hosts : new LinkedHashMap<>();
    }

    /**
     * Возвращает хост для указанного instanceId.
     *
     * @param instanceId идентификатор инстанса
     * @return хост или пустую строку, если не настроен
     */
    public @NonNull String getHost(@NonNull String instanceId) {
        HostPort hp = hosts.get(instanceId);
        return hp != null && hp.getHost() != null ? hp.getHost() : "";
    }

    /**
     * Возвращает порт для указанного instanceId.
     *
     * @param instanceId идентификатор инстанса
     * @return порт или 0, если не настроен
     */
    public int getPort(@NonNull String instanceId) {
        HostPort hp = hosts.get(instanceId);
        return hp != null && hp.getPort() != null ? hp.getPort() : 0;
    }

    /**
     * Проверяет, настроен ли хост/порт для указанного instanceId.
     */
    public boolean isConfigured(@NonNull String instanceId) {
        HostPort hp = hosts.get(instanceId);
        return hp != null
                && hp.getHost() != null && !hp.getHost().isBlank()
                && hp.getPort() != null && hp.getPort() > 0;
    }

    @PostConstruct
    public void logConfig() {
        if (hosts.isEmpty()) {
            log.warn("PrintSrv host configuration is empty. " +
                    "Set env variables like SCADA_MOBILE_PRINTSRV_TREPKO1_HOST=...");
        } else {
            log.info("PrintSrv hosts configured for {} instance(s): {}",
                    hosts.size(), String.join(", ", hosts.keySet()));
        }
    }

    // ─── Nested: HostPort ────────────────────────────────────────────────────

    public static class HostPort {
        private String host;
        private Integer port;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }
    }
}
