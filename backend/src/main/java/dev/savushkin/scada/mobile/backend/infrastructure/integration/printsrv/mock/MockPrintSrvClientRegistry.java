package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientSyncReport;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Реализация {@link PrintSrvClientRegistry} для профилей {@code dev} и {@code loadtest}.
 *
 * <p>Создаёт по одному {@link MockPrintSrvClient} для каждого активного инстанса
 * из {@link PrintSrvTopologyRepository} и инициализирует их seed-состояние,
 * загружая XML-файлы через {@link XmlSnapshotLoader}. При админ-изменениях
 * автоматов реестр приводится к состоянию БД вызовом {@link #synchronize()}
 * (без перезапуска приложения).
 *
 * <h3>Последовательность инициализации клиента</h3>
 * <ol>
 *   <li>Определить «offline»-флаг инстанса (см. ниже)</li>
 *   <li>Создать {@link MockInstanceState}</li>
 *   <li>Для каждого устройства из конфигурации инстанса вызвать
 *       {@link XmlSnapshotLoader#loadForDevice} и записать результат в состояние</li>
 *   <li>Загрузить дополнительные устройства, найденные в XML-файлах</li>
 *   <li>Обернуть состояние в {@link MockPrintSrvClient} с флагом offline</li>
 * </ol>
 *
 * <h3>Правила доступности инстанса</h3>
 * Мок не устанавливает реальных сетевых соединений, поэтому «недоступность»
 * эмулируется по декларативным правилам. Инстанс считается offline, если:
 * <ul>
 *   <li>он перечислен в {@link MockPrintSrvProperties#getOfflineInstances()}; или</li>
 *   <li>для него нет собственной seed-директории
 *       ({@code mock-snapshots/<instanceId>/} в classpath или
 *       {@code <baseDir>/<instanceId>/} на файловой системе) — это эмулирует
 *       несуществующий PrintSrv ID; или</li>
 *   <li>его host/port отличаются от baseline, зафиксированного при старте
 *       приложения — это эмулирует опрос по некорректному сетевому адресу
 *       (в prod-профиле то же самое обнаруживает TCP-клиент фактически).
 *       Возврат baseline-значений снова делает инстанс доступным.</li>
 * </ul>
 *
 * <p>Если в БД нет ни одного инстанса, Registry стартует
 * пустым и логирует предупреждение.
 */
@Component
@Profile({"dev", "loadtest"})
public class MockPrintSrvClientRegistry implements PrintSrvClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(MockPrintSrvClientRegistry.class);

    /**
     * Сетевые параметры инстанса на момент старта приложения.
     * Заполняется один раз в {@link #init()} и далее только читается.
     */
    private record ConnectionBaseline(String host, int port) {
    }

    private final PrintSrvTopologyRepository topologyRepo;
    private final MockPrintSrvProperties mockProperties;
    private final XmlSnapshotLoader snapshotLoader;

    /**
     * Атомарно заменяемая карта клиентов: читатели (poller-ы, симулятор) никогда
     * не наблюдают промежуточное состояние сверки.
     * Именно {@code LinkedHashMap} — чтобы порядок итерации соответствовал порядку в БД.
     */
    private volatile Map<String, MockPrintSrvClient> clients = Map.of();

    private final Map<String, ConnectionBaseline> connectionBaselines = new HashMap<>();

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public MockPrintSrvClientRegistry(
            PrintSrvTopologyRepository topologyRepo,
            MockPrintSrvProperties mockProperties,
            XmlSnapshotLoader snapshotLoader
    ) {
        this.topologyRepo = topologyRepo;
        this.mockProperties = mockProperties;
        this.snapshotLoader = snapshotLoader;
    }

    // ─── Инициализация ─────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        for (PrintSrvInstance inst : topologyRepo.findAllActiveInstances()) {
            connectionBaselines.put(inst.instanceId(), new ConnectionBaseline(inst.host(), inst.port()));
        }

        synchronize();
        if (clients.isEmpty()) {
            log.warn("MockPrintSrvClientRegistry: no active instances found in DB — " +
                     "no mock clients created. Check units table.");
            return;
        }

        log.info("MockPrintSrvClientRegistry: {} client(s) ready ({} offline)",
                 clients.size(),
                 clients.values().stream().filter(MockPrintSrvClient::isOffline).count());
    }

    // ─── PrintSrvClientRegistry API ────────────────────────────────────────

    @Override
    public synchronized PrintSrvClientSyncReport synchronize() {
        var instances = topologyRepo.findAllActiveInstances();
        Set<String> offline = Set.copyOf(mockProperties.getOfflineInstances());
        String baseDir = mockProperties.getSnapshotBaseDir();

        Map<String, MockPrintSrvClient> current = clients;
        Map<String, MockPrintSrvClient> next = new LinkedHashMap<>();
        Set<String> added = new LinkedHashSet<>();
        Set<String> restarted = new LinkedHashSet<>();

        for (var inst : instances) {
            String id = inst.instanceId();
            boolean connectionChanged = isConnectionChanged(inst);
            boolean shouldBeOffline =
                    offline.contains(id) || connectionChanged || !hasInstanceSeed(id, baseDir);

            MockPrintSrvClient existing = current.get(id);
            if (existing != null && existing.isOffline() == shouldBeOffline) {
                next.put(id, existing);
                continue;
            }
            // Клиент пересоздаётся при любом изменении желаемого состояния:
            // сменился PrintSrv ID (клиента не было), host/port (offline-флаг
            // у клиента иммутабелен) или вернулись корректные параметры.
            next.put(id, createClient(inst, shouldBeOffline, baseDir));
            if (existing != null) {
                restarted.add(id);
            } else {
                added.add(id);
            }
        }

        Set<String> removed = new LinkedHashSet<>(current.keySet());
        removed.removeAll(next.keySet());

        clients = Collections.unmodifiableMap(next);

        PrintSrvClientSyncReport report = new PrintSrvClientSyncReport(added, removed, restarted);
        if (!report.isEmpty()) {
            log.info("MockPrintSrvClientRegistry synchronized: added={}, removed={}, restarted={}",
                    added, removed, restarted);
        }
        return report;
    }

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

    // ─── Private helpers ───────────────────────────────────────────────────

    /**
     * Создаёт мок-клиент для инстанса с полной загрузкой seed-состояния.
     * Логика идентична первоначальной инициализации при старте приложения.
     *
     * @param shouldBeOffline итоговый offline-флаг клиента (конфиг, отклонение
     *                        host/port от baseline или отсутствие seed-данных)
     */
    private MockPrintSrvClient createClient(PrintSrvInstance inst, boolean shouldBeOffline, String baseDir) {
        String id = inst.instanceId();
        boolean isOffline = shouldBeOffline;

        // --- создаём изолированное состояние для инстанса ---
        MockInstanceState state = new MockInstanceState(id);

        // --- загружаем seed из XML для каждого устройства данного инстанса ---
        for (String device : inst.deviceNames()) {
            Map<String, String> props = snapshotLoader.loadForDevice(device, baseDir, id);
            state.initDevice(device, props);
        }

        // --- загружаем дополнительные устройства, найденные в XML-файлах ---
        Set<String> extraDevices = discoverExtraDevices(id, baseDir);
        for (String device : extraDevices) {
            if (!state.hasDevice(device)) {
                Map<String, String> props = snapshotLoader.loadForDevice(device, baseDir, id);
                state.initDevice(device, props);
            }
        }

        log.info("MockPrintSrv: registered instance '{}' (displayName='{}', offline={}, devices={})",
                 id, inst.displayName(), isOffline, state.getDeviceNames());
        return new MockPrintSrvClient(id, state, isOffline);
    }

    /**
     * Проверяет, отклонились ли host/port инстанса от baseline, зафиксированного
     * при старте. Для инстансов без baseline (появились в runtime) возвращает
     * {@code false} — их доступность определяется только seed-правилом.
     */
    private boolean isConnectionChanged(PrintSrvInstance inst) {
        ConnectionBaseline baseline = connectionBaselines.get(inst.instanceId());
        if (baseline == null) {
            return false;
        }
        return !baseline.host().equals(inst.host()) || baseline.port() != inst.port();
    }

    /**
     * Проверяет наличие собственной seed-директории инстанса
     * (classpath {@code mock-snapshots/<instanceId>/} или файловая
     * {@code <baseDir>/<instanceId>/}) хотя бы с одним XML-файлом.
     */
    private boolean hasInstanceSeed(String instanceId, String baseDir) {
        if (!discoverDevicesFromClasspath("mock-snapshots/" + instanceId + "/").isEmpty()) {
            return true;
        }
        if (baseDir == null) {
            return false;
        }
        Path fsDir = Path.of(baseDir, instanceId);
        if (!Files.isDirectory(fsDir)) {
            return false;
        }
        try (var stream = Files.list(fsDir)) {
            return stream.anyMatch(p -> p.getFileName().toString().endsWith("___Unit0.xml"));
        } catch (IOException e) {
            log.debug("[{}] Cannot check filesystem seed directory {}: {}", instanceId, fsDir, e.getMessage());
            return false;
        }
    }

    /**
     * Находит все устройства, для которых есть XML-файлы в classpath/fs,
     * но которых нет в {@link PrintSrvInstance#deviceNames()} (например, если в БД
     * не прописаны устройства, а XML-файлы есть).
     */
    private Set<String> discoverExtraDevices(String instanceId, String baseDir) {
        Set<String> result = new LinkedHashSet<>();

        // 1) Classpath default
        result.addAll(discoverDevicesFromClasspath("mock-snapshots/default/"));

        // 2) Classpath per-instance
        result.addAll(discoverDevicesFromClasspath("mock-snapshots/" + instanceId + "/"));

        // 3) Filesystem (если baseDir задан)
        if (baseDir != null) {
            Path fsDir = Path.of(baseDir, instanceId);
            if (Files.exists(fsDir) && Files.isDirectory(fsDir)) {
                try (var stream = Files.list(fsDir)) {
                    stream.filter(p -> p.getFileName().toString().endsWith("___Unit0.xml"))
                          .forEach(p -> {
                              String filename = p.getFileName().toString();
                              String device = filename.substring(0, filename.length() - "___Unit0.xml".length());
                              result.add(device);
                          });
                } catch (IOException e) {
                    log.warn("[{}] Cannot list filesystem directory {}: {}", instanceId, fsDir, e.getMessage());
                }
            }
        }

        return result;
    }

    private Set<String> discoverDevicesFromClasspath(String classpathDir) {
        Set<String> result = new LinkedHashSet<>();
        try {
            Resource[] resources = resourceResolver.getResources("classpath:" + classpathDir + "*___Unit0.xml");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.endsWith("___Unit0.xml")) {
                    String device = filename.substring(0, filename.length() - "___Unit0.xml".length());
                    result.add(device);
                }
            }
        } catch (IOException e) {
            log.debug("Cannot discover devices from classpath:{} — {}", classpathDir, e.getMessage());
        }
        return result;
    }
}
