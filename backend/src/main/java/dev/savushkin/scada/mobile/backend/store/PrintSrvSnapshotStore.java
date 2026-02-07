package dev.savushkin.scada.mobile.backend.store;

import dev.savushkin.scada.mobile.backend.dto.QueryAllResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory хранилище snapshot состояния PrintSrv.
 * <p>
 * Особенности реализации:
 * <ul>
 *   <li><b>Thread-safe</b>: используется {@link AtomicReference} для безопасного concurrent доступа</li>
 *   <li><b>Без истории</b>: хранит только последний snapshot (экономия памяти)</li>
 *   <li><b>Автоматическое обновление</b>: snapshot обновляется через {@link dev.savushkin.scada.mobile.backend.services.ScadaDataPollingService}</li>
 * </ul>
 * <p>
 * Паттерн использования:
 * <ol>
 *   <li>ScadaDataPollingService обновляет snapshot каждые 500ms</li>
 *   <li>CommandsService читает snapshot по запросу клиента</li>
 *   <li>Concurrent доступ безопасен благодаря AtomicReference</li>
 * </ol>
 */
@Component
public class PrintSrvSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(PrintSrvSnapshotStore.class);

    /**
     * Атомарная ссылка на последний snapshot (thread-safe)
     */
    private final AtomicReference<QueryAllResponseDTO> latestSnapshot = new AtomicReference<>();

    /**
     * Конструктор хранилища.
     * <p>
     * Инициализирует пустое хранилище. Первый snapshot появится
     * после первого успешного опроса PrintSrv.
     */
    public PrintSrvSnapshotStore() {
        log.info("PrintSrvSnapshotStore initialized");
    }

    /**
     * Сохраняет snapshot состояния PrintSrv.
     * <p>
     * Метод thread-safe и может безопасно вызываться из разных потоков
     * (например, из scheduled task ScadaDataPollingService).
     * <p>
     * Старый snapshot полностью заменяется новым (без слияния).
     *
     * @param snapshot новый snapshot состояния PrintSrv (не null)
     */
    public void saveSnapshot(QueryAllResponseDTO snapshot) {
        // Проверяем, это первый snapshot или обновление
        boolean isFirstSnapshot = latestSnapshot.get() == null;

        // Атомарно обновляем ссылку (thread-safe)
        latestSnapshot.set(snapshot);

        // Логируем по-разному для первого snapshot и обновлений
        if (isFirstSnapshot) {
            log.info("First snapshot saved with {} units", snapshot.units().size());
        } else {
            log.trace("Snapshot updated with {} units", snapshot.units().size());
        }
    }

    /**
     * Получает последний snapshot состояния PrintSrv.
     * <p>
     * Метод thread-safe и может безопасно вызываться из разных потоков
     * (например, из REST контроллеров).
     * <p>
     * <b>Важно:</b> Возвращает snapshot на момент вызова метода.
     * Данные могут устареть через ~500ms (частота polling).
     *
     * @return последний snapshot или null, если данных ещё нет
     * (приложение только запустилось и первый опрос не выполнен)
     */
    public QueryAllResponseDTO getSnapshot() {
        // Атомарно читаем ссылку (thread-safe)
        QueryAllResponseDTO snapshot = latestSnapshot.get();

        // Логируем результат
        if (snapshot == null) {
            log.warn("Attempted to get snapshot but store is empty");
        } else {
            log.trace("Snapshot retrieved with {} units", snapshot.units().size());
        }

        return snapshot;
    }
}
