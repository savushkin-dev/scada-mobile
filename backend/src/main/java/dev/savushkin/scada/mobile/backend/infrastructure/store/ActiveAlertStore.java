package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Потокобезопасное in-memory хранилище активных алёртов.
 * <p>
 * Выполняет две функции:
 * <ol>
 *   <li><b>Snapshot при коннекте</b> — {@link #getAll()} возвращает список всех текущих
 *       активных алёртов; {@code LiveWsHandler} отправляет его новым клиентам как
 *       {@code ALERT_SNAPSHOT} сразу после установки соединения.</li>
 *   <li><b>Дельта-детектор</b> — {@link #updateAndDiff(String, AlertMessageDTO)} атомарно
 *       обновляет состояние одного аппарата и возвращает дельту для live-рассылки и
 *       уведомлений.</li>
 * </ol>
 * <p>
 * <b>Error signature</b> — хэш состава ошибок, сгруппированных по имени устройства и количеству.
 * Позволяет обнаруживать изменение причины аварии внутри уже активного алёрта без
 * повторной рассылки при неизменном состоянии.
 * Формат: {@code "Device1:N,Device2:M"} (устройства отсортированы по алфавиту).
 * <p>
 * Данные хранятся в {@link ConcurrentHashMap} (ключ — {@code unitId}).
 * Метод {@link #updateAndDiff(String, AlertMessageDTO)} синхронизирован для атомарной пары
 * «сравни + обнови» на уровне конкретного аппарата.
 */
@Component
public class ActiveAlertStore {

    /**
     * unitId → активный алёрт
     */
    private final ConcurrentHashMap<String, AlertMessageDTO> store = new ConcurrentHashMap<>();

    /**
     * unitId → error signature активного алёрта (для сравнения без повторной рассылки)
     */
    private final ConcurrentHashMap<String, String> signatureStore = new ConcurrentHashMap<>();

    /**
     * Возвращает снимок всех текущих активных алёртов.
     * Потокобезопасен — возвращает неизменяемую копию.
     */
    public List<AlertMessageDTO> getAll() {
        return List.copyOf(store.values());
    }

    /**
     * Атомарно обновляет состояние одного аппарата и возвращает локальную дельту.
     *
     * <p>Логика дельты:
     * <ul>
     *   <li>Не было → появился: {@code added=[currentAlert]}</li>
     *   <li>Был → исчез: {@code removed=[oldAlert]}</li>
     *   <li>Был → тот же состав ошибок: {@code Delta([], [])}</li>
     *   <li>Был → состав ошибок изменился: {@code added=[currentAlert]}, чтобы
     *       уведомить пользователей о новой причине аварии.</li>
     * </ul>
     *
     * <p>Если алёрт переходит в статус «появился заново» из-за изменения состава ошибок,
     * store обновляется новым алёртом (timestamp из текущего опроса).
     *
     * @param unitId        идентификатор аппарата
     * @param currentAlert  текущий активный алёрт или {@code null}, если алёрта нет
     * @return локальная дельта по одному аппарату
     */
    public synchronized Delta updateAndDiff(@NonNull String unitId, @Nullable AlertMessageDTO currentAlert) {
        AlertMessageDTO existing = store.get(unitId);

        if (currentAlert == null) {
            if (existing == null) {
                return new Delta(List.of(), List.of());
            }
            store.remove(unitId);
            signatureStore.remove(unitId);
            return new Delta(List.of(), List.of(existing));
        }

        if (existing == null) {
            String signature = computeErrorSignature(currentAlert.errors());
            store.put(unitId, currentAlert);
            signatureStore.put(unitId, signature);
            return new Delta(List.of(currentAlert), List.of());
        }

        // Алёрт уже был активен — проверяем, изменился ли состав ошибок.
        String newSignature = computeErrorSignature(currentAlert.errors());
        String existingSignature = signatureStore.getOrDefault(unitId, "");
        if (!newSignature.equals(existingSignature)) {
            // Состав ошибок изменился — обновляем store и уведомляем.
            store.put(unitId, currentAlert);
            signatureStore.put(unitId, newSignature);
            return new Delta(List.of(currentAlert), List.of());
        }

        return new Delta(List.of(), List.of());
    }

    /**
     * Вычисляет стабильный error signature для набора ошибок.
     * Группирует ошибки по имени устройства и считает количество на каждом.
     * Устройства сортируются по алфавиту для стабильного сравнения.
     *
     * <p>Пример: ошибки Printer11×1, CamChecker×2 → {@code "CamChecker:2,Printer11:1"}.
     *
     * @param errors список ошибок алёрта
     * @return строковый signature, пустая строка если errors пуст
     */
    public static @NonNull String computeErrorSignature(@NonNull List<AlertErrorDTO> errors) {
        if (errors.isEmpty()) {
            return "";
        }
        Map<String, Long> grouped = new TreeMap<>(
                errors.stream()
                        .collect(Collectors.groupingBy(AlertErrorDTO::device, Collectors.counting()))
        );
        return grouped.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    /**
     * Результат сравнения двух срезов алёртов.
     *
     * @param added   Алёрты, которые появились или изменили состав ошибок.
     * @param removed Алёрты, которые исчезли (были активны, теперь нет).
     */
    public record Delta(List<AlertMessageDTO> added, List<AlertMessageDTO> removed) {
    }
}
