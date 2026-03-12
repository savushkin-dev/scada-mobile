package dev.savushkin.scada.mobile.backend.infrastructure.store;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное in-memory хранилище активных ошибок устройств по аппарату.
 *
 * <p>Является <b>единственным источником правды</b> об ошибках устройств:
 * обновляется после каждого scan cycle (через {@link #update}) и читается
 * двумя независимыми потребителями:
 * <ul>
 *   <li>{@code AlertService} — определяет наличие алёрта для канала {@code /ws/live};
 *       цвет карточки аппарата синхронен с содержимым этого store.</li>
 *   <li>{@code UnitDetailService} — формирует payload {@code ERRORS} для канала
 *       {@code /ws/unit/{unitId}}; вкладка «Журнал» отражает те же ошибки.</li>
 * </ul>
 *
 * <p>Запись — {@link DeviceError} — представляет одну <b>активную</b> ошибку.
 * Неактивные ошибки ({@code value = "0"}) в store не попадают.
 */
@Component
public class UnitErrorStore {

    /**
     * unitId → список активных ошибок устройств этого аппарата.
     * Отсутствие ключа семантически эквивалентно пустому списку.
     */
    private final ConcurrentHashMap<String, List<DeviceError>> store = new ConcurrentHashMap<>();

    /**
     * Обновляет список активных ошибок для аппарата.
     *
     * <p>Если {@code errors} пустой — запись удаляется из store
     * (освобождение памяти и явная семантика «нет ошибок»).
     *
     * @param unitId идентификатор аппарата
     * @param errors актуальный список активных ошибок (может быть пустым)
     */
    public void update(@NonNull String unitId, @NonNull List<DeviceError> errors) {
        if (errors.isEmpty()) {
            store.remove(unitId);
        } else {
            store.put(unitId, List.copyOf(errors));
        }
    }

    /**
     * Возвращает список активных ошибок для аппарата.
     *
     * @param unitId идентификатор аппарата
     * @return неизменяемый список ошибок, или пустой список если ошибок нет / аппарат неизвестен
     */
    public @NonNull List<DeviceError> getErrors(@NonNull String unitId) {
        return store.getOrDefault(unitId, Collections.emptyList());
    }

    /**
     * Возвращает {@code true}, если у аппарата есть хотя бы одна активная ошибка.
     *
     * @param unitId идентификатор аппарата
     */
    public boolean hasErrors(@NonNull String unitId) {
        List<DeviceError> errors = store.get(unitId);
        return errors != null && !errors.isEmpty();
    }
}
