package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.PrintSrvMapper;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Поллер для одного инстанса PrintSrv.
 *
 * <p>Не является Spring-бином: создаётся фабрично через {@link PrintSrvPollerFactory}.
 * Один экземпляр на весь жизненный цикл приложения — состояние изолировано
 * от остальных инстансов.
 *
 * <h3>Логика опроса</h3>
 * Каждый scan-цикл поллер пробует опросить все устройства инстанса.
 * Критерий «инстанс недоступен»: <b>все</b> устройства вернули IOException.
 * Если хотя бы одно ответило — соединение считается живым.
 *
 * <h3>Умное логирование</h3>
 * Уровни подобраны так, чтобы в prod (INFO) не засорять вывод типичными
 * событиями, но не пропускать важные переходы:
 * <ul>
 *   <li>Первый сбой → {@code WARN} (что-то пошло не так)</li>
 *   <li>Повторные сбои → {@code DEBUG} (тишина в prod)</li>
 *   <li>Восстановление → {@code INFO} (важно для оператора)</li>
 *   <li>Штатный poll → {@code TRACE} (только при явной отладке)</li>
 * </ul>
 *
 * <h3>Graceful degradation</h3>
 * {@link InstanceSnapshotRepository} не очищается при сбоях — клиенты получают
 * последний валидный snapshot.
 */
public final class PrintSrvInstancePoller {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvInstancePoller.class);

    private final PrintSrvClient client;
    private final PrintSrvMapper mapper;
    private final InstanceSnapshotRepository snapshotRepo;
    private final List<String> devices;
    /**
     * Последнее известное состояние доступности инстанса.
     * Нужен для детекции перехода reachable -> unreachable.
     */
    private boolean wasReachable;

    /**
     * Package-private: создаётся только через {@link PrintSrvPollerFactory}.
     */
    PrintSrvInstancePoller(
            @NonNull PrintSrvClient client,
            PrintSrvMapper mapper,
            InstanceSnapshotRepository snapshotRepo,
            @NonNull List<String> devices
    ) {
        this.client = client;
        this.mapper = mapper;
        this.snapshotRepo = snapshotRepo;
        this.devices = List.copyOf(devices);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Логический идентификатор инстанса (например, {@code "bosch"}).
     */
    public String getInstanceId() {
        return client.getInstanceId();
    }

    /**
     * Выполняет один poll-цикл для данного инстанса.
     *
     * <p>Опрашивает все устройства, сконфигурированные для данного инстанса. Результаты успешных запросов
     * сохраняются в репозиторий. Ошибки отдельных устройств логируются на уровне
     * {@code TRACE} и не прерывают опрос остальных.
     */
    public PollResult poll() {
        boolean anySuccess = false;

        for (String device : devices) {
            try {
                QueryAllResponseDTO dto = client.queryAll(device);
                DeviceSnapshot snapshot = mapper.toDomainDeviceSnapshot(dto);
                snapshotRepo.save(client.getInstanceId(), device, snapshot);
                anySuccess = true;
            } catch (IOException e) {
                log.trace("[{}] device='{}' unreachable: {}", client.getInstanceId(), device, e.getMessage());
            }
        }

        boolean availabilityChanged = anySuccess != wasReachable;

        if (anySuccess) {
            if (availabilityChanged) {
                log.info("[{}] PrintSrv connection restored", client.getInstanceId());
            }
            wasReachable = true;
            log.trace("[{}] poll ok", client.getInstanceId());
            return new PollResult(true, availabilityChanged);
        }

        // Недоступно: при первом переходе в unreachable очищаем stale-снапшоты,
        // чтобы API/WS сразу начали отдавать "Нет данных".
        if (availabilityChanged) {
            snapshotRepo.clearInstance(client.getInstanceId());
            wasReachable = false;
            log.warn("[{}] PrintSrv unreachable for all configured devices", client.getInstanceId());
            return new PollResult(false, true);
        }

        log.trace("[{}] still unreachable", client.getInstanceId());
        return new PollResult(false, false);
    }

    public int getConfiguredDeviceCount() {
        return devices.size();
    }

    /**
     * Результат одного poll-цикла инстанса.
     *
     * @param reachable            true, если хотя бы одно устройство ответило
     * @param availabilityChanged  true, если доступность изменилась относительно предыдущего цикла
     */
    public record PollResult(boolean reachable, boolean availabilityChanged) {
        /**
         * Когда нужно публиковать live-обновление в WS:
         * - всегда при reachable=true (новые данные);
         * - при смене доступности (reachable -> unreachable), чтобы мгновенно деградировать UI.
         */
        public boolean shouldPublishLiveUpdate() {
            return reachable || availabilityChanged;
        }
    }
}
