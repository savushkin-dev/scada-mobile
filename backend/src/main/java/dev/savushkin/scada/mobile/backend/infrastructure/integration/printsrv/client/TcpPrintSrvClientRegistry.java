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
 * из {@link PrintSrvTopologyRepository}. Клиенты создаются лениво в
 * {@link PostConstruct} после готовности контекста и БД.
 * При shutdown приложения закрывает все TCP-соединения.
 */
@Component
@Profile("prod")
public class TcpPrintSrvClientRegistry implements PrintSrvClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(TcpPrintSrvClientRegistry.class);

    private final PrintSrvTopologyRepository topologyRepo;
    private final PrintSrvProperties props;
    private final ObjectMapper objectMapper;
    private Map<String, TcpPrintSrvClient> clients = Map.of();

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
        int connectTimeout = props.getSocket().getConnectTimeoutMs();
        int readTimeout = props.getSocket().getReadTimeoutMs();

        Map<String, TcpPrintSrvClient> map = new LinkedHashMap<>();
        for (PrintSrvInstance inst : topologyRepo.findAllActiveInstances()) {
            TcpPrintSrvClient client = new TcpPrintSrvClient(
                    inst.instanceId(), inst.host(), inst.port(),
                    connectTimeout, readTimeout, objectMapper);
            map.put(inst.instanceId(), client);
        }
        this.clients = Collections.unmodifiableMap(map);
        log.info("TcpPrintSrvClientRegistry initialized with {} instances", clients.size());
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
