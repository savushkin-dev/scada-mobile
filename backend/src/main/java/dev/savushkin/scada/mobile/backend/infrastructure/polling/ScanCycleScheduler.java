package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.infrastructure.ws.ScanCycleCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Единый Scan Cycle планировщик для всех профилей (dev и prod).
 *
 * <p>На каждом цикле делегирует опрос каждого инстанса его
 * {@link PrintSrvInstancePoller}-у, который инкапсулирует трёхрежимную машину
 * состояний (NORMAL → RECONNECTING → RECOVERY).
 *
 * <h3>Режимы работы поллера</h3>
 * <ul>
 *   <li><b>NORMAL</b> — опрос каждый scan-цикл; единичные ошибки просто логируются.</li>
 *   <li><b>RECONNECTING</b> — после N последовательных сбоев: до {@code maxAttempts}
 *       попыток переподключения с экспоненциальным backoff внутри текущего цикла.</li>
 *   <li><b>RECOVERY</b> — все попытки исчерпаны: проверка раз в {@code recoveryCheckIntervalMs};
 *       клиенты получают последний валидный snapshot (graceful degradation).</li>
 * </ul>
 *
 * <h3>Изоляция инстансов</h3>
 * Каждый инстанс имеет свой изолированный поллер и счётчик ошибок.
 * Offline-инстанс (например, {@code bosch} в dev) не блокирует остальные и не
 * путает их счётчики.
 *
 * <h3>Интервал опроса</h3>
 * <ul>
 *   <li>dev  — {@code printsrv.polling.fixed-delay-ms: 5000} (5 сек)</li>
 *   <li>prod — {@code printsrv.polling.fixed-delay-ms:  500} (0.5 сек)</li>
 * </ul>
 */
@Service
public class ScanCycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScanCycleScheduler.class);

    /**
     * Устройства, опрашиваемые на каждом инстансе PrintSrv.
     * Список одинаков для всех инстансов согласно документации протокола.
     */
    static final List<String> DEVICES = List.of(
            "Line",
            "scada",
            "BatchQueue",
            "Printer11",
            "CamAgregation",
            "CamAgregationBox",
            "CamChecker"
    );

    private final List<PrintSrvInstancePoller> pollers;
    private final ApplicationEventPublisher eventPublisher;

    public ScanCycleScheduler(
            PrintSrvPollerFactory pollerFactory,
            ApplicationEventPublisher eventPublisher
    ) {
        this.pollers = pollerFactory.createAll();
        this.eventPublisher = eventPublisher;
        log.info("ScanCycleScheduler initialized: {} instance poller(s), {} devices per instance",
                pollers.size(), DEVICES.size());
    }

    /**
     * Scan cycle: опрашивает все инстансы через их поллеры.
     *
     * <p>Каждый поллер самостоятельно управляет retry-логикой для своего инстанса.
     * После обхода всех инстансов публикует {@link ScanCycleCompletedEvent} —
     * сигнал для {@code StatusBroadcaster} разослать обновления по WebSocket.
     */
    @Scheduled(fixedDelayString = "${printsrv.polling.fixed-delay-ms:5000}")
    public void scanCycle() {
        for (PrintSrvInstancePoller poller : pollers) {
            poller.poll(DEVICES);
        }
        // Уведомляем StatusBroadcaster: snapshots обновлены, можно рассылать статус по WS
        eventPublisher.publishEvent(new ScanCycleCompletedEvent(this));
    }
}
