package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;

import java.io.IOException;

/**
 * Абстракция одного TCP-соединения с инстансом PrintSrv.
 *
 * <p>Одна физическая машина маркировки = один {@code PrintSrvClient}.
 * Идентичность машины определяется исключительно {@code instanceId} (host:port
 * в реальной реализации), а не именами устройств внутри неё — они одинаковы
 * на всех машинах ({@code Line}, {@code BatchQueue}, {@code Printer11}, и т.д.).
 *
 * <p>Все реализации должны быть thread-safe: polling scheduler и обработчики
 * REST-запросов могут вызывать методы одновременно из разных потоков.
 *
 * <p>Контракт по исключениям:
 * <ul>
 *   <li>{@link IOException} — транспортная или протокольная ошибка. Caller должен
 *       считать соединение потенциально битым и обработать retry-логику выше.</li>
 *   <li>Unchecked — только при нарушении предусловий (null-arguments и т.п.).</li>
 * </ul>
 */
public interface PrintSrvClient {

    /**
     * @return логический идентификатор инстанса, заданный в конфигурации
     *         (например, {@code "trepko1"}, {@code "hassia2"}).
     *         Никогда не null, не меняется на протяжении жизни бина.
     */
    String getInstanceId();

    /**
     * Выполняет команду {@code QueryAll} для указанного устройства.
     *
     * <p>Возвращает полный снапшот состояния: все юниты устройства, их свойства,
     * счётчики и статусы.
     *
     * @param deviceName имя устройства на инстансе, например {@code "Line"},
     *                   {@code "scada"}, {@code "Printer11"}
     * @return полный ответ QueryAll; никогда не null
     * @throws IOException если инстанс недоступен, соединение разорвано или
     *                     ответ не удалось десериализовать
     */
    QueryAllResponseDTO queryAll(String deviceName) throws IOException;

    /**
     * Проверяет, способен ли клиент прямо сейчас общаться с инстансом.
     *
     * @return {@code true}, если клиент считает себя работоспособным
     */
    boolean isAlive();
}
