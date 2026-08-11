package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Отчёт о сверке реестра клиентов PrintSrv с актуальной топологией из БД.
 *
 * @param added     инстансы, для которых клиент создан впервые
 * @param removed   инстансы, исчезнувшие из активной топологии (клиент закрыт)
 * @param restarted инстансы с изменившимися параметрами подключения (host/port) —
 *                  клиент пересоздан
 */
public record PrintSrvClientSyncReport(Set<String> added, Set<String> removed, Set<String> restarted) {

    public static PrintSrvClientSyncReport empty() {
        return new PrintSrvClientSyncReport(Set.of(), Set.of(), Set.of());
    }

    /**
     * {@code true}, если сверка не выявила изменений.
     */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && restarted.isEmpty();
    }

    /**
     * Инстансы, чьи сохранённые snapshot-ы устарели и подлежат очистке:
     * исчезнувшие из топологии и переподключённые (их старые данные относились
     * к прежнему подключению).
     */
    public Set<String> staleSnapshotIds() {
        Set<String> result = new LinkedHashSet<>(removed);
        result.addAll(restarted);
        return result;
    }

    /**
     * Все затронутые сверкой инстансы — для них нужно немедленно разослать
     * актуальный статус («Нет данных») и разрешить зависшие алёрты.
     */
    public Set<String> affectedIds() {
        Set<String> result = new LinkedHashSet<>(added);
        result.addAll(removed);
        result.addAll(restarted);
        return result;
    }
}
