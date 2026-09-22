package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvInstancePolledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Детектор сигнала «последняя партия» из polling-потока PrintSrv.
 * <p>
 * Событийная (edge) семантика: markserver при получении команды
 * {@code LINE_CMD_FINISH_BATCH (113)} инкрементирует счётчик в свойстве
 * {@code FinishBatch} устройства {@code Line} (значение циклически 1→100→1,
 * см. markserver-libs PR #43). Детектор сравнивает значение свойства между
 * poll-циклами: любое изменение — событие «последняя партия», при котором
 * активируется MACHINE-уведомление с создателем-инстансом.
 * <p>
 * Так как счётчик не несёт сигнала «конец», уведомление живёт, пока его
 * не снимет сотрудник вручную через UI (toggle «последняя партия»).
 * Детектор сам активное уведомление никогда не снимает — в том числе при
 * потере снапшота (обрыв связи с инстансом): сбрасывается только baseline
 * счётчика, чтобы восстановление связи не дало ложного срабатывания.
 * <p>
 * Первое наблюдение значения после старта/восстановления — только baseline,
 * без активации: иначе рестарт бэкенда ловил бы ложные срабатывания.
 * Уведомления, установленные работником (USER), детектор никогда не трогает.
 * <p>
 * Состояние (baseline счётчика) хранится в памяти на инстанс: после рестарта
 * бэкенда baseline пересоздаётся с первого poll. Любое исключение из
 * репозитория/сервиса ловится и логируется WARN-ом: падение детектора не
 * должно ронять polling worker.
 */
@Component
public class LineBatchEndDetector {

    private static final Logger log = LoggerFactory.getLogger(LineBatchEndDetector.class);

    private final InstanceSnapshotRepository snapshotRepository;
    private final NotificationService notificationService;
    private final PrintSrvProperties printSrvProperties;

    /** Последнее наблюдаемое значение счётчика FinishBatch по инстансу. */
    private final Map<String, String> lastSeenValues = new ConcurrentHashMap<>();

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
     * Обрабатывает завершение одного polling-прохода по инстансу: детектирует
     * изменение счётчика «последней партии» и активирует MACHINE-уведомление.
     * Снятие уведомления — только вручную сотрудником, детектор его не делает.
     *
     * @param event событие завершения опроса инстанса PrintSrv
     */
    @EventListener
    public void onInstancePolled(PrintSrvInstancePolledEvent event) {
        String instanceId = event.instanceId();
        try {
            PrintSrvProperties.BatchEndProperties batchEnd = printSrvProperties.getBatchEnd();
            DeviceSnapshot snapshot = snapshotRepository.get(instanceId, batchEnd.getDeviceName());
            String currentValue = snapshot == null ? null : snapshot.units().values().stream()
                    .map(unit -> unit.properties().getRawProperties().get(batchEnd.getPropertyName()))
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);

            if (currentValue == null) {
                // Снапшот недоступен или свойство исчезло — сбрасываем baseline,
                // чтобы восстановление сигнала не дало ложного срабатывания.
                // Активное уведомление не трогаем: снятие — только вручную.
                lastSeenValues.remove(instanceId);
                return;
            }

            String previousValue = lastSeenValues.put(instanceId, currentValue);
            if (previousValue == null) {
                // Первое наблюдение — только baseline, без активации.
                return;
            }
            if (!previousValue.equals(currentValue)) {
                notificationService.activateMachineNotificationIfAbsent(instanceId, instanceId);
                log.info("Batch-end counter changed on instanceId='{}' ({} -> {})",
                        instanceId, previousValue, currentValue);
            }
        } catch (Exception ex) {
            log.warn("Batch-end detector failed for instanceId='{}': {}",
                    instanceId, ex.getMessage(), ex);
        }
    }
}
