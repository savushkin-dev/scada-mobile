package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory реализация {@link InstanceSnapshotRepository}.
 * <p>
 * Хранит snapshot-ы всех устройств для каждого инстанса PrintSrv
 * в потокобезопасной структуре {@link ConcurrentHashMap}.
 * <p>
 * Структура: {@code instanceId → (deviceName → DeviceSnapshot)}.
 * <p>
 * Эта реализация:
 * <ul>
 *   <li><b>Thread-safe</b>: {@link ConcurrentHashMap} гарантирует безопасный
 *       concurrent доступ из polling-потока и REST-потоков.</li>
 *   <li><b>Без истории</b>: хранит только последний snapshot каждого устройства.</li>
 *   <li><b>Заменяема</b>: если потребуется переход на БД, достаточно
 *       создать новую реализацию {@link InstanceSnapshotRepository}.</li>
 * </ul>
 */
@Component
public class InMemoryInstanceSnapshotStore implements InstanceSnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryInstanceSnapshotStore.class);

    /**
     * instanceId → (deviceName → DeviceSnapshot)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, DeviceSnapshot>> store =
            new ConcurrentHashMap<>();

    @Override
    public void save(String instanceId, String deviceName, DeviceSnapshot snapshot) {
        store.computeIfAbsent(instanceId, k -> new ConcurrentHashMap<>())
                .put(deviceName, snapshot);
        log.trace("Snapshot saved: instance='{}', device='{}', units={}",
                instanceId, deviceName, snapshot.getUnitCount());
    }

    @Override
    public DeviceSnapshot get(String instanceId, String deviceName) {
        ConcurrentHashMap<String, DeviceSnapshot> devices = store.get(instanceId);
        if (devices == null) {
            return null;
        }
        return devices.get(deviceName);
    }

    @Override
    public Map<String, DeviceSnapshot> getAllForInstance(String instanceId) {
        ConcurrentHashMap<String, DeviceSnapshot> devices = store.get(instanceId);
        if (devices == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(devices);
    }

    @Override
    public void clearInstance(String instanceId) {
        ConcurrentHashMap<String, DeviceSnapshot> removed = store.remove(instanceId);
        if (removed != null) {
            log.info("Snapshot store cleared for instance='{}', removedDevices={}", instanceId, removed.size());
        }
    }

    @Override
    public boolean hasAnySnapshot() {
        return !store.isEmpty();
    }
}
