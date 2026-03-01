package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Реализация {@link PrintSrvClientRegistry} для профиля {@code dev}.
 *
 * <p>При старте контекста создаёт по одному {@link MockPrintSrvClient} для каждого
 * инстанса из {@code printsrv.instances} и инициализирует их seed-состояние,
 * загружая XML-файлы через {@link XmlSnapshotLoader}.
 *
 * <h3>Последовательность инициализации</h3>
 * <ol>
 *   <li>Считать список инстансов из {@link PrintSrvProperties}</li>
 *   <li>Определить множество «offline»-инстансов из {@link MockPrintSrvProperties}</li>
 *   <li>Для каждого инстанса создать {@link MockInstanceState}</li>
 *   <li>Для каждого из 7 известных устройств вызвать
 *       {@link XmlSnapshotLoader#loadForDevice} и записать результат в состояние</li>
 *   <li>Обернуть состояние в {@link MockPrintSrvClient} с флагом offline</li>
 * </ol>
 *
 * <p>Если в {@code printsrv.instances} нет ни одного элемента, Registry стартует
 * пустым и логирует предупреждение — это помогает при первоначальной настройке,
 * когда YAML ещё не полностью заполнен.
 */
@Component
@Profile("dev")
public class MockPrintSrvClientRegistry implements PrintSrvClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(MockPrintSrvClientRegistry.class);

    private final PrintSrvProperties printsrvProperties;
    private final MockPrintSrvProperties mockProperties;
    private final XmlSnapshotLoader snapshotLoader;

    /**
     * Именно {@code LinkedHashMap} — чтобы порядок итерации соответствовал порядку в YAML.
     * Не менять на HashMap: в логах и UI порядок должен быть предсказуемым.
     */
    private final Map<String, MockPrintSrvClient> clients = new LinkedHashMap<>();

    public MockPrintSrvClientRegistry(
            PrintSrvProperties printsrvProperties,
            MockPrintSrvProperties mockProperties,
            XmlSnapshotLoader snapshotLoader
    ) {
        this.printsrvProperties = printsrvProperties;
        this.mockProperties = mockProperties;
        this.snapshotLoader = snapshotLoader;
    }

    // ─── Инициализация ─────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        var instances = printsrvProperties.getInstances();
        if (instances.isEmpty()) {
            log.warn("MockPrintSrvClientRegistry: printsrv.instances is empty — " +
                     "no mock clients created. Check application-dev.yaml.");
            return;
        }

        Set<String> offline = Set.copyOf(mockProperties.getOfflineInstances());
        String baseDir = mockProperties.getSnapshotBaseDir();

        for (var inst : instances) {
            String id = inst.getId();
            boolean isOffline = offline.contains(id);

            // --- создаём изолированное состояние для инстанса ---
            MockInstanceState state = new MockInstanceState(id);

            // --- загружаем seed из XML для каждого устройства ---
            for (String device : XmlSnapshotLoader.KNOWN_DEVICES) {
                Map<String, String> props = snapshotLoader.loadForDevice(device, baseDir, id);
                state.initDevice(device, props);
            }

            MockPrintSrvClient client = new MockPrintSrvClient(id, state, isOffline);
            clients.put(id, client);

            log.info("MockPrintSrv: registered instance '{}' (displayName='{}', offline={})",
                     id, inst.getDisplayName(), isOffline);
        }

        log.info("MockPrintSrvClientRegistry: {} client(s) ready ({} offline)",
                 clients.size(),
                 clients.values().stream().filter(MockPrintSrvClient::isOffline).count());
    }

    // ─── PrintSrvClientRegistry API ────────────────────────────────────────

    @Override
    public PrintSrvClient get(String instanceId) {
        MockPrintSrvClient client = clients.get(instanceId);
        if (client == null) {
            throw new NoSuchElementException(
                    "MockPrintSrvClientRegistry: no client registered for instanceId='%s'. " +
                    "Known ids: %s".formatted(instanceId, clients.keySet()));
        }
        return client;
    }

    @Override
    public Collection<PrintSrvClient> getAll() {
        return Collections.unmodifiableCollection(clients.values());
    }

    @Override
    public Set<String> getInstanceIds() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    // ─── Пакетный API для MockStateSimulator ───────────────────────────────

    /**
     * Возвращает все мок-клиенты с сохранением типа {@link MockPrintSrvClient}.
     *
     * <p>Используется {@link MockStateSimulator} и юнит-тестами,
     * которым нужен прямой доступ к состоянию без downcast.
     * Доступен только внутри пакета.
     */
    Collection<MockPrintSrvClient> getAllMock() {
        return Collections.unmodifiableCollection(clients.values());
    }

    /**
     * Возвращает мок-клиент по ID.
     *
     * <p>Возвращает пустой Optional если инстанс не найден или реестр не инициализирован.
     */
    Optional<MockPrintSrvClient> getMock(String instanceId) {
        return Optional.ofNullable(clients.get(instanceId));
    }
}
