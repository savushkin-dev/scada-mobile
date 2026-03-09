package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.client.PrintSrvClient;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.PropertiesDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.UnitsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Мок-реализация {@link PrintSrvClient} для одного инстанса PrintSrv.
 *
 * <p>Не является Spring-бином: создаётся фабрично через {@link MockPrintSrvClientRegistry}.
 * Это позволяет иметь 14 изолированных экземпляров без накладных расходов контекста.
 *
 * <h3>Потокобезопасность</h3>
 * Класс сам по себе stateless (вся мутабельность в {@link MockInstanceState});
 * метод {@code queryAll} делегирует в state, где стоит
 * ReadWriteLock. Параллельные вызовы безопасны.
 *
 * <h3>Режим offline</h3>
 * Если {@code offline=true} — {@code queryAll} немедленно кидает
 * {@link IOException} с диагностическим сообщением. Это позволяет тестировать
 * поведение polling-слоя при недоступности конкретного аппарата.
 */
public class MockPrintSrvClient implements PrintSrvClient {

    private static final Logger log = LoggerFactory.getLogger(MockPrintSrvClient.class);

    /**
     * Фиксированное имя единственного юнита в протоколе QueryAll.
     * В файлах — {@code Unit0}, в ответе сервера — {@code "u1"} (1-based).
     */
    private static final String UNIT_KEY = "u1";

    private final String instanceId;
    private final MockInstanceState state;
    private final boolean offline;

    /**
     * @param instanceId  ID инстанса из конфигурации
     * @param state       полностью инициализированное состояние (после загрузки XML)
     * @param offline     если {@code true}, все операции сразу кидают IOException
     */
    public MockPrintSrvClient(String instanceId, MockInstanceState state, boolean offline) {
        this.instanceId = instanceId;
        this.state = state;
        this.offline = offline;
    }

    // ─── PrintSrvClient API ───────────────────────────────────────────────────

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public QueryAllResponseDTO queryAll(String deviceName) throws IOException {
        checkOnline("queryAll", deviceName);

        log.trace("[{}] queryAll({})", instanceId, deviceName);

        Map<String, String> props = state.getPropertiesCopy(deviceName);

        // Derive synthetic UnitsDTO fields from raw properties
        String stateValue = props.getOrDefault("ST", "0");
        String task       = props.getOrDefault("CurItem", "");
        Integer counter   = extractLeadingInt(task);

        UnitsDTO unit = new UnitsDTO(stateValue, task, counter, buildPropertiesDto(props));

        // QueryAll ответ: одно устройство, один юнит ("u1")
        return new QueryAllResponseDTO(deviceName, "QueryAll", Map.of(UNIT_KEY, unit));
    }

    @Override
    public boolean isAlive() {
        return !offline;
    }

    // ─── Package-private helpers used by MockStateSimulator ──────────────────

    /**
     * Возвращает состояние инстанса для прямого use в симуляторе.
     *
     * <p>Пакетный доступ намеренен: симулятор находится в том же пакете и работает
     * с состоянием напрямую, минуя overhead сборки DTO.
     */
    MockInstanceState getState() {
        return state;
    }

    boolean isOffline() {
        return offline;
    }

    // ─── Private: helpers ────────────────────────────────────────────────────

    /**
     * Бросает IOException если инстанс сконфигурирован как offline.
     * Название операции передаётся для диагностики в тестах.
     */
    private void checkOnline(String operation, String deviceName) throws IOException {
        if (offline) {
            throw new IOException(
                    "MockPrintSrv instance '%s' is configured as offline (operation=%s, device=%s)"
                            .formatted(instanceId, operation, deviceName));
        }
    }

    /**
     * Пытается распарсить первое целое число из строки.
     *
     * <p>Формат {@code CurItem} в Line и принтерах: {@code "1605 | 147 | 19.08.2025"}.
     * Первое число — счётчик маркировок. Для других устройств, где CurItem не числовой,
     * возвращает null.
     */
    private static Integer extractLeadingInt(String curItem) {
        if (curItem == null || curItem.isBlank()) {
            return null;
        }
        // Берём первый токен до '|' или пробела
        String firstToken = curItem.split("[|\\s]")[0].trim();
        try {
            return Integer.parseInt(firstToken);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Конструирует {@link PropertiesDTO} из сырой карты свойств.
     *
     * <p>Маппинг ключей соответствует {@code @JsonProperty}-аннотациям в {@link PropertiesDTO},
     * потому что именно эти строковые ключи PrintSrv кладёт как в JSON-ответ, так и в XML-файлы
     * конфигурации. Если ключа нет в карте — поле в DTO останется null (PrintSrv-протокол
     * допускает отсутствие любого поля).
     */
    private static PropertiesDTO buildPropertiesDto(Map<String, String> p) {
        return new PropertiesDTO(
                parseIntOrNull(p.get("command")),
                p.get("message"),
                p.get("Error"),
                p.get("ErrorMessage"),
                p.get("cmdsuccess"),
                p.get("ST"),
                p.get("batchId"),
                p.get("CurItem"),
                p.get("batchIdCodesQueue"),
                p.get("setBatchID"),
                p.get("devChangeBatch"),
                p.get("devsChangeBatchIDQueueControl"),
                p.get("devType"),
                p.get("LineID"),
                p.get("OnChangeBatchPrinters"),
                p.get("Level1Printers"),
                p.get("Level2Printers"),
                p.get("OnChangeBatchCams"),
                p.get("Level1Cams"),
                p.get("Level2Cams"),
                p.get("SignalCams"),
                p.get("LineDevices"),
                p.get("enableErrors")
        );
    }

    /**
     * Безопасный парсинг Integer из строки. null или не-число → null.
     */
    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
