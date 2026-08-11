package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Реестр реальных TCP-клиентов PrintSrv для prod-профиля.
 * <p>
 * Создаёт по одному {@link TcpPrintSrvClient} на каждый активный инстанс
 * из {@link PrintSrvTopologyRepository}. Клиенты создаются в
 * {@link PostConstruct} после готовности контекста и БД, а при админ-изменениях
 * автоматов реестр приводится к состоянию БД вызовом {@link #synchronize()}
 * (без перезапуска приложения).
 * При shutdown приложения закрывает все TCP-соединения.
 */
@Component
@Profile("prod")
public class TcpPrintSrvClientRegistry implements PrintSrvClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(TcpPrintSrvClientRegistry.class);

    private final PrintSrvTopologyRepository topologyRepo;
    private final PrintSrvProperties props;
    private final ObjectMapper objectMapper;
    /**
     * Атомарно заменяемая карта клиентов: читатели (poller-ы) никогда не наблюдают
     * промежуточное состояние сверки.
     */
    private volatile Map<String, TcpPrintSrvClient> clients = Map.of();

    public TcpPrintSrvClientRegistry(
            PrintSrvTopologyRepository topologyRepo,
            PrintSrvProperties props,
            ObjectMapper objectMapper
    ) {
        this.topologyRepo = topologyRepo;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        synchronize();
        log.info("TcpPrintSrvClientRegistry initialized with {} instances", clients.size());
    }

    @Override
    public synchronized PrintSrvClientSyncReport synchronize() {
        int connectTimeout = props.getSocket().getConnectTimeoutMs();
        int readTimeout = props.getSocket().getReadTimeoutMs();

        Map<String, TcpPrintSrvClient> current = clients;
        Map<String, TcpPrintSrvClient> next = new LinkedHashMap<>();
        Set<String> added = new LinkedHashSet<>();
        Set<String> restarted = new LinkedHashSet<>();

        for (PrintSrvInstance inst : topologyRepo.findAllActiveInstances()) {
            String id = inst.instanceId();
            TcpPrintSrvClient existing = current.get(id);
            if (existing != null && existing.getHost().equals(inst.host()) && existing.getPort() == inst.port()) {
                next.put(id, existing);
                continue;
            }
            if (existing != null) {
                existing.close();
                restarted.add(id);
            } else {
                added.add(id);
            }
            next.put(id, new TcpPrintSrvClient(id, inst.host(), inst.port(),
                    connectTimeout, readTimeout, objectMapper));
        }

        Set<String> removed = new LinkedHashSet<>();
        for (Map.Entry<String, TcpPrintSrvClient> entry : current.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                entry.getValue().close();
                removed.add(entry.getKey());
            }
        }

        clients = Collections.unmodifiableMap(next);

        PrintSrvClientSyncReport report = new PrintSrvClientSyncReport(added, removed, restarted);
        if (!report.isEmpty()) {
            log.info("TcpPrintSrvClientRegistry synchronized: added={}, removed={}, restarted={}",
                    added, removed, restarted);
        }
        return report;
    }

    @Override
    public PrintSrvClient get(String instanceId) {
        PrintSrvClient client = clients.get(instanceId);
        if (client == null) {
            throw new NoSuchElementException("Unknown instance: " + instanceId);
        }
        return client;
    }

    @Override
    public Collection<PrintSrvClient> getAll() {
        return Collections.unmodifiableCollection(clients.values());
    }

    @Override
    public Set<String> getInstanceIds() {
        return clients.keySet();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down TcpPrintSrvClientRegistry ({} clients)", clients.size());
        for (TcpPrintSrvClient client : clients.values()) {
            client.close();
        }
    }
}
