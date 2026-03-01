package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Реестр всех {@link PrintSrvClient}-ов, сконфигурированных для данного приложения.
 *
 * <p>Единственное место, откуда вышестоящие компоненты (polling scheduler, command
 * executor) получают доступ к клиентам по инстансу. Реестр не знает о бизнес-логике —
 * только о топологии (какие инстансы существуют).
 *
 * <p>Реализации:
 * <ul>
 *   <li>{@code MockPrintSrvClientRegistry} — {@code @Profile("dev")}, 14 изолированных
 *       in-memory состояний.</li>
 *   <li>Реальная TCP-реализация — планируется для {@code @Profile("prod")}.</li>
 * </ul>
 */
public interface PrintSrvClientRegistry {

    /**
     * Возвращает клиент для инстанса с указанным ID.
     *
     * @param instanceId логический ID инстанса (например, {@code "hassia2"})
     * @return соответствующий {@link PrintSrvClient}; никогда не null
     * @throws NoSuchElementException если {@code instanceId} не зарегистрирован
     */
    PrintSrvClient get(String instanceId);

    /**
     * @return неизменяемое представление всех зарегистрированных клиентов
     *         (включая сконфигурированные как "offline")
     */
    Collection<PrintSrvClient> getAll();

    /**
     * @return множество всех зарегистрированных instanceId
     */
    Set<String> getInstanceIds();
}
