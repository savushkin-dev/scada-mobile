package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Фабрика поллеров: создаёт по одному {@link PrintSrvInstancePoller} на каждый
 * зарегистрированный инстанс PrintSrv.
 *
 * <h3>Паттерн Factory</h3>
 * Разделяет ответственность: {@link PrintSrvClientRegistry} знает о соединениях,
 * {@link PrintSrvInstancePoller} — о логике опроса, а фабрика — о том, как
 * соединить клиент с поллером. {@link ScanCycleScheduler} остаётся чистым —
 * он только итерирует готовые поллеры.
 *
 * <h3>Spring-инициализация</h3>
 * Бин создаётся до {@link ScanCycleScheduler}, который инжектирует эту фабрику
 * и вызывает {@link #createAll()} в конструкторе. Порядок гарантируется
 * стандартным Spring DI (зависимость по типу).
 */
@Component
public class PrintSrvPollerFactory {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvPollerFactory.class);

    private final PrintSrvClientRegistry registry;
    private final PrintSrvMapper mapper;
    private final InstanceSnapshotRepository snapshotRepo;

    public PrintSrvPollerFactory(
            PrintSrvClientRegistry registry,
            PrintSrvMapper mapper,
            InstanceSnapshotRepository snapshotRepo
    ) {
        this.registry = registry;
        this.mapper = mapper;
        this.snapshotRepo = snapshotRepo;
    }

    /**
     * Создаёт список поллеров для всех зарегистрированных инстансов.
     *
     * <p>Порядок следует порядку, возвращяемому {@link PrintSrvClientRegistry#getAll()}.
     * Оба реестра ({@code MockPrintSrvClientRegistry} и {@code TcpPrintSrvClientRegistry})
     * используют {@code LinkedHashMap}, поэтому порядок соответствует порядку в YAML.
     *
     * @return неизменяемый список поллеров (по одному на инстанс)
     */
    public List<PrintSrvInstancePoller> createAll() {
        List<PrintSrvInstancePoller> pollers = registry.getAll()
                .stream()
                .map(this::createFor)
                .toList();

        log.info("PrintSrvPollerFactory: created {} instance poller(s)", pollers.size());
        return pollers;
    }

    /**
     * Создаёт поллер для конкретного клиента.
     * Используется как в {@link #createAll()}, так и в тестах напрямую.
     *
     * @param client уже сконфигурированный клиент PrintSrv
     * @return новый поллер с изолированным {@link InstanceConnectionState}
     */
    public PrintSrvInstancePoller createFor(@NonNull PrintSrvClient client) {
        log.debug("PrintSrvPollerFactory: creating poller for instance '{}'", client.getInstanceId());
        return new PrintSrvInstancePoller(client, mapper, snapshotRepo);
    }
}
