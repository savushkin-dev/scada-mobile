package dev.savushkin.scada.mobile.backend.domain.model;

import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Разница между составом устройств в БД и runtime-снапшотом PrintSrv.
 *
 * @param added    устройства, которые есть в runtime, но отсутствуют в БД
 * @param removed  устройства, которые есть в БД, но отсутствуют в runtime
 */
public record CompositionDiff(
        @NonNull Set<String> added,
        @NonNull Set<String> removed
) {

    public CompositionDiff {
        added = Set.copyOf(added);
        removed = Set.copyOf(removed);
    }

    /**
     * Вычисляет разницу между двумя составами устройств.
     *
     * @param db       состав из БД
     * @param runtime  состав из runtime-снапшота
     * @return разница (added / removed)
     */
    public static @NonNull CompositionDiff of(@NonNull DeviceComposition db, @NonNull DeviceComposition runtime) {
        Set<String> dbSet = db.allDevices();
        Set<String> runtimeSet = runtime.allDevices();

        Set<String> added = new HashSet<>(runtimeSet);
        added.removeAll(dbSet);

        Set<String> removed = new HashSet<>(dbSet);
        removed.removeAll(runtimeSet);

        return new CompositionDiff(added, removed);
    }

    /**
     * Пустая разница (нет расхождений).
     */
    public static @NonNull CompositionDiff empty() {
        return new CompositionDiff(Set.of(), Set.of());
    }

    /**
     * true, если нет расхождений (added и removed пусты).
     */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }

    /**
     * Все устройства, участвующие в diff (added ∪ removed).
     */
    public @NonNull Set<String> allChanges() {
        Set<String> all = new HashSet<>(added);
        all.addAll(removed);
        return Collections.unmodifiableSet(all);
    }
}
