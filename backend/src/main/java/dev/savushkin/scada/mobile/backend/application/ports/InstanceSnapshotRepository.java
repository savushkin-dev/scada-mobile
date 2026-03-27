package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;

import java.util.Map;

/**
 * Порт хранения snapshot-ов устройств для каждого инстанса PrintSrv.
 * <p>
 * Реализация может быть in-memory ({@code ConcurrentHashMap}), а в будущем —
 * база данных. Вышестоящие слои (сервисы, контроллеры) зависят только от
 * этого интерфейса и не знают о конкретном хранилище.
 * <p>
 * Все методы должны быть thread-safe: polling-поток записывает snapshot-ы,
 * а REST-потоки читают их одновременно.
 */
public interface InstanceSnapshotRepository {

    /**
     * Сохраняет snapshot одного устройства для указанного инстанса.
     *
     * @param instanceId идентификатор инстанса (например, {@code "hassia2"})
     * @param deviceName имя устройства (например, {@code "Line"}, {@code "scada"})
     * @param snapshot   snapshot состояния устройства
     */
    void save(String instanceId, String deviceName, DeviceSnapshot snapshot);

    /**
     * Возвращает snapshot конкретного устройства инстанса.
     *
     * @param instanceId идентификатор инстанса
     * @param deviceName имя устройства
     * @return snapshot или {@code null}, если данные ещё не получены
     */
    DeviceSnapshot get(String instanceId, String deviceName);

    /**
     * Возвращает все device snapshot-ы для указанного инстанса.
     *
     * @param instanceId идентификатор инстанса
     * @return неизменяемая карта {@code deviceName → DeviceSnapshot}; пустая, если данных нет
     */
    Map<String, DeviceSnapshot> getAllForInstance(String instanceId);

    /**
     * Полностью очищает все snapshot-ы указанного инстанса.
     *
     * <p>Используется при переходе инстанса в состояние unreachable,
     * чтобы API/WS немедленно отдавали "Нет данных", а не устаревшее
     * последнее состояние.
     *
     * @param instanceId идентификатор инстанса
     */
    void clearInstance(String instanceId);

    /**
     * Проверяет, получен ли хотя бы один snapshot (любого инстанса, любого устройства).
     *
     * @return {@code true}, если хранилище не пустое
     */
    boolean hasAnySnapshot();
}
