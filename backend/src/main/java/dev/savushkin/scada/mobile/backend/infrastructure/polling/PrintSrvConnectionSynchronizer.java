package dev.savushkin.scada.mobile.backend.infrastructure.polling;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.domain.model.UnitChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientRegistry;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClientSyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Приводит слой подключений к PrintSrv в соответствие с БД при админ-изменениях
 * автоматов — без перезапуска приложения.
 *
 * <p>Порядок обработки {@link UnitChangedEvent} (после коммита транзакции):
 * <ol>
 *   <li>{@link PrintSrvClientRegistry#synchronize()} — создать клиентов новых
 *       инстансов, закрыть исчезнувшие, пересоздать клиентов с изменившимися
 *       host/port;</li>
 *   <li>{@link PrintSrvPollingRuntime#synchronize()} — запустить/остановить
 *       polling-worker-ы под изменившийся набор клиентов;</li>
 *   <li>очистить устаревшие snapshot-ы удалённых и переподключённых инстансов,
 *       чтобы API/WS перестали отдавать данные прежнего подключения;</li>
 *   <li>опубликовать {@link PrintSrvInstancePolledEvent} для всех затронутых
 *       инстансов — {@code StatusBroadcaster} немедленно разошлёт актуальный
 *       статус («Нет данных»), разрешит зависшие алёрты и обновит детали
 *       аппарата у подписчиков.</li>
 * </ol>
 *
 * <p>В результате смена PrintSrv ID / host / port в админке мгновенно переводит
 * карточку аппарата в состояние «Нет данных», а возврат корректных значений
 * восстанавливает опрос без рестарта backend.
 */
@Component
public class PrintSrvConnectionSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvConnectionSynchronizer.class);

    private final PrintSrvClientRegistry clientRegistry;
    private final PrintSrvPollingRuntime pollingRuntime;
    private final InstanceSnapshotRepository snapshotRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PrintSrvConnectionSynchronizer(
            PrintSrvClientRegistry clientRegistry,
            PrintSrvPollingRuntime pollingRuntime,
            InstanceSnapshotRepository snapshotRepo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.clientRegistry = clientRegistry;
        this.pollingRuntime = pollingRuntime;
        this.snapshotRepo = snapshotRepo;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUnitChanged(UnitChangedEvent event) {
        PrintSrvClientSyncReport report = clientRegistry.synchronize();
        pollingRuntime.synchronize();
        if (report.isEmpty()) {
            return;
        }

        log.info("PrintSrv connections reconciled (unit {} {}): added={}, removed={}, restarted={}",
                event.unitId(), event.action(), report.added(), report.removed(), report.restarted());

        for (String instanceId : report.staleSnapshotIds()) {
            snapshotRepo.clearInstance(instanceId);
        }
        for (String instanceId : report.affectedIds()) {
            eventPublisher.publishEvent(new PrintSrvInstancePolledEvent(instanceId));
        }
    }
}
