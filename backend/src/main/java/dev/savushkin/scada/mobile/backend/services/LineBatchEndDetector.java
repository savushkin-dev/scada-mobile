package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvInstancePolledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Детектор сигнала «последняя партия» из polling-потока PrintSrv.
 * <p>
 * Уровневая семантика: пока в поле {@code command} устройства {@code Line}
 * стоит код из конфигурации (по умолчанию {@code 113}) — активно
 * MACHINE-уведомление «последняя партия» с создателем-инстансом; когда код
 * исчезает (другое значение, тег пропал, снапшот недоступен) — уведомление
 * снимается. Уведомления, установленные работником (USER), детектор
 * никогда не трогает.
 * <p>
 * Идемпотентность достигается сверкой уровня сигнала (снапшот) с уровнем
 * уведомления (БД) на каждом poll-цикле; отдельный in-memory edge-трекер
 * не используется. Повторные вызовы при неизменном уровне — дешёвые no-op
 * в {@link NotificationService}, поэтому логировать их не нужно.
 * <p>
 * Любое исключение из репозитория/сервиса ловится и логируется WARN-ом:
 * падение детектора не должно ронять polling worker.
 */
@Component
public class LineBatchEndDetector {

    private static final Logger log = LoggerFactory.getLogger(LineBatchEndDetector.class);

    private final InstanceSnapshotRepository snapshotRepository;
    private final NotificationService notificationService;
    private final PrintSrvProperties printSrvProperties;

    public LineBatchEndDetector(
            InstanceSnapshotRepository snapshotRepository,
            NotificationService notificationService,
            PrintSrvProperties printSrvProperties
    ) {
        this.snapshotRepository = snapshotRepository;
        this.notificationService = notificationService;
        this.printSrvProperties = printSrvProperties;
    }

    /**
     * Обрабатывает завершение одного polling-прохода по инстансу: сводит
     * уровень сигнала «последняя партия» из снапшота с состоянием уведомления.
     *
     * @param event событие завершения опроса инстанса PrintSrv
     */
    @EventListener
    public void onInstancePolled(PrintSrvInstancePolledEvent event) {
        String instanceId = event.instanceId();
        try {
            PrintSrvProperties.BatchEndProperties batchEnd = printSrvProperties.getBatchEnd();
            DeviceSnapshot snapshot = snapshotRepository.get(instanceId, batchEnd.getDeviceName());
            boolean signalActive = snapshot != null && snapshot.units().values().stream()
                    .map(unit -> unit.properties().getCommand().orElse(null))
                    .anyMatch(command -> command != null && command == batchEnd.getCommandCode());

            if (signalActive) {
                notificationService.activateMachineNotificationIfAbsent(instanceId, instanceId);
            } else {
                notificationService.deactivateMachineNotificationIfPresent(instanceId, instanceId);
            }
        } catch (Exception ex) {
            log.warn("Batch-end detector failed for instanceId='{}': {}",
                    instanceId, ex.getMessage(), ex);
        }
    }
}
