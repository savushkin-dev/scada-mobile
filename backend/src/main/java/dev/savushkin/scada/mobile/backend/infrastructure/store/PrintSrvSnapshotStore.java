package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.application.ports.DeviceSnapshotReader;
import dev.savushkin.scada.mobile.backend.application.ports.DeviceSnapshotWriter;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.polling.PrintSrvPollingScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory хранилище snapshot состояния SCADA системы.
 * <p>
 * Теперь хранит domain модель {@link DeviceSnapshot} вместо DTO.
 * Это обеспечивает:
 * <ul>
 *   <li><b>Независимость от протокола</b>: изменения PrintSrv DTO не влияют на store</li>
 *   <li><b>Типобезопасность</b>: domain модели обеспечивают строгие инварианты</li>
 *   <li><b>Чистую архитектуру</b>: store не зависит от внешних слоев</li>
 * </ul>
 * <p>
 * Особенности реализации:
 * <ul>
 *   <li><b>Thread-safe</b>: используется {@link java.util.concurrent.atomic.AtomicReference} для безопасного concurrent доступа</li>
 *   <li><b>Без истории</b>: хранит только последний snapshot (экономия памяти)</li>
 *   <li><b>Автоматическое обновление</b>: snapshot обновляется через
 *   {@link PrintSrvPollingScheduler}</li>
 * </ul>
 * <p>
 * Паттерн использования:
 * <ol>
 *   <li>PrintSrvPollingScheduler обновляет snapshot с интервалом, заданным в конфигурации
 *   (<code>printsrv.polling.fixed-delay-ms</code>)</li>
 *   <li>Application Service читает snapshot по запросу клиента</li>
 *   <li>Concurrent доступ безопасен благодаря AtomicReference</li>
 * </ol>
 */
@Component
public class PrintSrvSnapshotStore implements DeviceSnapshotReader, DeviceSnapshotWriter {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvSnapshotStore.class);

    /**
     * Атомарная ссылка на последний snapshot (thread-safe)
     */
    private final AtomicReference<DeviceSnapshot> latestSnapshot = new AtomicReference<>();

    /**
     * Конструктор хранилища.
     * <p>
     * Инициализирует пустое хранилище. Первый snapshot появится
     * после первого успешного опроса PrintSrv.
     */
    public PrintSrvSnapshotStore() {
        log.debug("PrintSrvSnapshotStore initialized");
    }

    @Override
    public void save(DeviceSnapshot snapshot) {
        saveSnapshot(snapshot);
    }

    /**
     * Сохраняет snapshot состояния SCADA системы.
     * <p>
     * Метод thread-safe и может безопасно вызываться из разных потоков
     * (например, из scheduled task PrintSrvPollingScheduler).
     * <p>
     * Старый snapshot полностью заменяется новым (без слияния).
     *
     * @param snapshot новый snapshot состояния (не null)
     */
    public void saveSnapshot(DeviceSnapshot snapshot) {
        // Проверяем, это первый snapshot или обновление
        boolean isFirstSnapshot = latestSnapshot.get() == null;

        // Атомарно обновляем ссылку (thread-safe)
        latestSnapshot.set(snapshot);

        // Логируем по-разному для первого snapshot и обновлений
        if (isFirstSnapshot) {
            log.info("First snapshot saved with {} units", snapshot.getUnitCount());
        } else {
            log.trace("Snapshot updated with {} units", snapshot.getUnitCount());
        }
    }

    @Override
    public DeviceSnapshot getLatestOrNull() {
        return getSnapshot();
    }

    /**
     * Получает последний snapshot состояния SCADA системы.
     * <p>
     * Метод thread-safe и может безопасно вызываться из разных потоков
     * (например, из application services).
     * <p>
     * <b>Важно:</b> Возвращает snapshot на момент вызова метода.
     * Данные могут устареть примерно на величину интервала polling
     * (<code>printsrv.polling.fixed-delay-ms</code>).
     *
     * @return последний snapshot или null, если данных ещё нет
     * (приложение только запустилось и первый опрос не выполнен)
     */
    public DeviceSnapshot getSnapshot() {
        // Атомарно читаем ссылку (thread-safe)
        DeviceSnapshot snapshot = latestSnapshot.get();

        // Логируем результат
        if (snapshot == null) {
            log.warn("Attempted to get snapshot but store is empty");
        } else {
            log.trace("Snapshot retrieved with {} units", snapshot.getUnitCount());
        }

        return snapshot;
    }
}
