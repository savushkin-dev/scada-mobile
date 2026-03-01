package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;

import java.io.IOException;
import java.util.Map;

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
     * счётчики и статусы. Все значения — строки (PrintSrv-протокол передаёт
     * только строки; конвертация типов — обязанность domain-слоя).
     *
     * @param deviceName имя устройства на инстансе, например {@code "Line"},
     *                   {@code "scada"}, {@code "Printer11"}
     * @return полный ответ QueryAll; никогда не null, но может содержать пустой
     *         {@code units}-map, если устройство не имеет активных юнитов
     * @throws IOException если инстанс недоступен, соединение разорвано или
     *                     ответ не удалось десериализовать
     */
    QueryAllResponseDTO queryAll(String deviceName) throws IOException;

    /**
     * Выполняет команду {@code SetUnitVars} — записывает набор свойств в юнит устройства.
     *
     * <p>Семантика последнего записи: если в {@code parameters} передан ключ, который
     * уже есть в устройстве, новое значение перезаписывает старое.
     *
     * @param deviceName имя устройства (например, {@code "Line"})
     * @param unitNumber номер юнита, 1-based (в файлах — {@code Unit0}, в протоколе — unit=1)
     * @param parameters пары ключ→значение для записи; значения — строки; не null, не пустой
     * @throws IOException             если инстанс недоступен или команда не выполнилась
     * @throws IllegalArgumentException если {@code parameters} null или пустой
     */
    void setUnitVars(String deviceName, int unitNumber, Map<String, String> parameters) throws IOException;

    /**
     * Проверяет, способен ли клиент прямо сейчас общаться с инстансом.
     *
     * <p>Для реальной TCP-реализации — проверяет состояние сокета.
     * Для мок-реализации — возвращает {@code !isOffline}.
     * Этот метод не должен кидать исключений.
     *
     * @return {@code true}, если клиент считает себя работоспособным
     */
    boolean isAlive();
}
