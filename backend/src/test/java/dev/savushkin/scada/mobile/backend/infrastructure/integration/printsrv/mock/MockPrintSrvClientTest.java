package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.PropertiesDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.QueryAllResponseDTO;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.dto.UnitsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тесты mock-инфраструктуры PrintSrv.
 *
 * <p>Намеренно НЕ загружают Spring-контекст: объекты создаются вручную.
 * Это даёт субсекундное время прогона и полную изоляцию от конфигурации.
 *
 * <h3>Покрываемые сценарии</h3>
 * <ol>
 *   <li>{@link MockPrintSrvClient}: queryAll → корректная DTO из состояния</li>
 *   <li>{@link MockPrintSrvClient}: offline → IOException с диагностическим сообщением</li>
 *   <li>{@link MockStateSimulator#incrementCurItemCounter}: корректный парсинг форматов</li>
 *   <li>{@link MockStateSimulator}: tick() инкрементирует счётчики детерминированно</li>
 *   <li>{@link XmlSnapshotLoader}: graceful degradation при отсутствии файла</li>
 *   <li>{@link XmlSnapshotLoader}: реальная загрузка из classpath</li>
 * </ol>
 */
class MockPrintSrvClientTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Вспомогательные фабричные методы
    // ─────────────────────────────────────────────────────────────────────────

    /** Создаёт состояние с заданными свойствами в устройстве "Line". */
    private static MockInstanceState stateWithLine(Map<String, String> lineProps) {
        MockInstanceState state = new MockInstanceState("test-instance");
        state.initDevice("Line", lineProps);
        return state;
    }

    /** Создаёт состояние с заданными свойствами в устройстве {@code deviceName}. */
    private static MockInstanceState stateWith(String deviceName, Map<String, String> props) {
        MockInstanceState state = new MockInstanceState("test-instance");
        state.initDevice(deviceName, props);
        return state;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MockPrintSrvClient — online behaviour
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MockPrintSrvClient — online mode")
    class OnlineBehaviourTests {

        @Test
        @DisplayName("queryAll возвращает корректный DTO: state, task, counter маппятся из карты свойств")
        void queryAll_mapsStateTaskCounterFromProperties() throws Exception {
            Map<String, String> props = Map.of(
                    "ST", "1",
                    "CurItem", "1605 | 147 | 19.08.2025",
                    "Error", "0",
                    "LineID", "TREPKO_1"
            );
            MockPrintSrvClient client = new MockPrintSrvClient("trepko1", stateWithLine(props), false);

            QueryAllResponseDTO dto = client.queryAll("Line");

            assertEquals("Line", dto.deviceName());
            assertEquals("QueryAll", dto.command());
            assertTrue(dto.units().containsKey("u1"), "Должен быть ключ «u1»");

            UnitsDTO unit = dto.units().get("u1");
            assertEquals("1", unit.state());
            assertEquals("1605 | 147 | 19.08.2025", unit.task());
            assertEquals(1605, unit.counter(), "Первый токен CurItem должен стать counter");

            PropertiesDTO p = unit.properties();
            assertEquals("1",       p.st());
            assertEquals("0",       p.error());
            assertEquals("TREPKO_1", p.lineId());
        }

        @Test
        @DisplayName("queryAll: counter=null когда CurItem не начинается с числа")
        void queryAll_counterIsNullForNonNumericCurItem() throws Exception {
            Map<String, String> props = Map.of("CurItem", "NO_NUMBER | data");
            MockPrintSrvClient client = new MockPrintSrvClient("h1", stateWithLine(props), false);

            UnitsDTO unit = client.queryAll("Line").units().get("u1");
            assertNull(unit.counter(), "Ненумерический CurItem → counter=null");
        }

        @Test
        @DisplayName("queryAll: пустой CurItem → counter=null, task=''")
        void queryAll_emptyCurItemGivesNullCounterAndEmptyTask() throws Exception {
            MockPrintSrvClient client = new MockPrintSrvClient("h1", stateWithLine(Map.of()), false);

            UnitsDTO unit = client.queryAll("Line").units().get("u1");
            assertNull(unit.counter());
            assertEquals("", unit.task());
        }

        @Test
        @DisplayName("queryAll возвращает пустые DTO для неинициализированного устройства")
        void queryAll_unknownDevice_returnsEmptyUnit() throws Exception {
            MockInstanceState state = new MockInstanceState("x");
            MockPrintSrvClient client = new MockPrintSrvClient("x", state, false);

            // Устройство "BatchQueue" не инициализировано — state отдаст пустую карту
            QueryAllResponseDTO dto = client.queryAll("BatchQueue");
            UnitsDTO unit = dto.units().get("u1");

            assertEquals("0", unit.state(),    "ST absent → '0'");
            assertEquals("",  unit.task(),     "CurItem absent → ''");
            assertNull(unit.counter());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MockPrintSrvClient — offline behaviour
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MockPrintSrvClient — offline mode")
    class OfflineBehaviourTests {

        private final MockPrintSrvClient offlineClient = new MockPrintSrvClient(
                "bosch", new MockInstanceState("bosch"), true);

        @Test
        @DisplayName("isAlive() = false для offline-инстанса")
        void isAlive_returnsFalseWhenOffline() {
            assertFalse(offlineClient.isAlive());
        }

        @Test
        @DisplayName("queryAll бросает IOException с именем инстанса")
        void queryAll_throwsIOException() {
            IOException ex = assertThrows(IOException.class,
                    () -> offlineClient.queryAll("Line"));
            assertTrue(ex.getMessage().contains("bosch"),
                    "Сообщение должно упоминать instanceId");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MockStateSimulator — incrementCurItemCounter
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MockStateSimulator.incrementCurItemCounter")
    class IncrementCurItemCounterTests {

        @Test
        @DisplayName("Стандартный формат 'N | M | date' → '(N+1) | M | date'")
        void standardFormat() {
            assertEquals(
                    "1606 | 147 | 19.08.2025",
                    MockStateSimulator.incrementCurItemCounter("1605 | 147 | 19.08.2025")
            );
        }

        @Test
        @DisplayName("Только число без разделителя → число + 1")
        void numberOnly() {
            assertEquals("43", MockStateSimulator.incrementCurItemCounter("42"));
        }

        @Test
        @DisplayName("Пустая строка возвращается без изменений")
        void emptyString() {
            assertEquals("", MockStateSimulator.incrementCurItemCounter(""));
        }

        @Test
        @DisplayName("null возвращается как пустая строка")
        void nullInput() {
            assertEquals("", MockStateSimulator.incrementCurItemCounter(null));
        }

        @Test
        @DisplayName("Нечисловое начало: строка не изменяется")
        void nonNumericLeading() {
            String original = "NO_NUM | data";
            assertEquals(original, MockStateSimulator.incrementCurItemCounter(original));
        }

        @Test
        @DisplayName("Пробелы вокруг числа корректно обрабатываются")
        void whitespaceAroundNumber() {
            // "  5  | rest" → "6 | rest"
            String result = MockStateSimulator.incrementCurItemCounter("  5  | rest");
            assertEquals("6 | rest", result);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MockStateSimulator — tick
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MockStateSimulator — tick behaviour")
    class SimulatorTickTests {

        /** Создаёт полностью настроенный симулятор с контролируемыми параметрами. */
        private MockStateSimulator buildSimulator(
                MockPrintSrvClientRegistry registry,
                double errorFlipProb,
                long seed
        ) {
            MockPrintSrvProperties props = new MockPrintSrvProperties();
            props.setErrorFlipProbability(errorFlipProb);
            props.setRandomSeed(seed);
            props.setSimulationEnabled(true);
            return new MockStateSimulator(registry, props);
        }

        /** Строит реестр с одним инстансом и заданными начальными данными устройства. */
        private MockPrintSrvClientRegistry singleInstanceRegistry(
                String instanceId,
                String deviceName,
                Map<String, String> props
        ) {
            MockInstanceState state = new MockInstanceState(instanceId);
            state.initDevice(deviceName, props);
            MockPrintSrvClient client = new MockPrintSrvClient(instanceId, state, false);

            // Используем реальный реестр, но наполняем его через тестовый subclass
            return new MockPrintSrvClientRegistry(null, null, null) {
                @Override
                java.util.Collection<MockPrintSrvClient> getAllMock() {
                    return List.of(client);
                }
            };
        }

        @Test
        @DisplayName("CamAgregation: Total и Succeeded растут после тика при ST=1")
        void camAggregation_totalAndSucceededIncrease() {
            Map<String, String> props = Map.of("ST", "1", "Total", "100", "Succeeded", "95");
            MockPrintSrvClientRegistry registry =
                    singleInstanceRegistry("trepko1", "CamAgregation", props);

            // seed=42, errorFlipProbability=0.0 — никаких ошибок
            MockStateSimulator sim = buildSimulator(registry, 0.0, 42L);
            sim.tickAll();

            // Получаем обновлённое состояние клиента
            MockPrintSrvClient client = registry.getAllMock().iterator().next();
            Map<String, String> updated = client.getState().getPropertiesCopy("CamAgregation");

            int total     = Integer.parseInt(updated.get("Total"));
            int succeeded = Integer.parseInt(updated.get("Succeeded"));

            assertTrue(total > 100,     "Total должен вырасти");
            assertTrue(succeeded > 95,  "Succeeded должен вырасти");
        }

        @Test
        @DisplayName("Line: CurItem counter растёт при ST=1")
        void line_curItemCounterIncreases() {
            Map<String, String> props = Map.of(
                    "ST", "1",
                    "CurItem", "1000 | 50 | 01.01.2025"
            );
            MockPrintSrvClientRegistry registry =
                    singleInstanceRegistry("trepko1", "Line", props);

            MockStateSimulator sim = buildSimulator(registry, 0.0, 1L);
            sim.tickAll();

            MockPrintSrvClient client = registry.getAllMock().iterator().next();
            String newCurItem = client.getState().getPropertiesCopy("Line").get("CurItem");

            String[] parts = newCurItem.split("\\|");
            int newCounter = Integer.parseInt(parts[0].trim());
            assertEquals(1001, newCounter, "Счётчик должен увеличиться на 1");
        }

        @Test
        @DisplayName("Line: CurItem не меняется при ST=0")
        void line_curItemDoesNotChangeWhenInactive() {
            Map<String, String> props = Map.of(
                    "ST", "0",
                    "CurItem", "500 | 50 | 01.01.2025"
            );
            MockPrintSrvClientRegistry registry =
                    singleInstanceRegistry("trepko1", "Line", props);

            // Без вероятности ошибок, seed не имеет значения
            MockStateSimulator sim = buildSimulator(registry, 0.0, 1L);
            sim.tickAll();

            MockPrintSrvClient client = registry.getAllMock().iterator().next();
            String curItem = client.getState().getPropertiesCopy("Line").get("CurItem");

            assertEquals("500 | 50 | 01.01.2025", curItem, "Неактивная линия не меняет CurItem");
        }

        @Test
        @DisplayName("Offline-инстанс полностью пропускается симулятором")
        void offlineInstance_isSkippedBySimulator() {
            MockInstanceState state = new MockInstanceState("bosch");
            state.initDevice("CamAgregation", Map.of("ST", "1", "Total", "0", "Succeeded", "0"));
            MockPrintSrvClient offlineClient = new MockPrintSrvClient("bosch", state, true);

            MockPrintSrvClientRegistry registry = new MockPrintSrvClientRegistry(null, null, null) {
                @Override
                java.util.Collection<MockPrintSrvClient> getAllMock() {
                    return List.of(offlineClient);
                }
            };

            MockStateSimulator sim = buildSimulator(registry, 0.0, 1L);
            sim.tickAll();

            // Total не изменился
            assertEquals("0", state.getPropertiesCopy("CamAgregation").get("Total"),
                    "Состояние offline-инстанса не должно изменяться");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // XmlSnapshotLoader — загрузка и graceful degradation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("XmlSnapshotLoader")
    class XmlSnapshotLoaderTests {

        private final XmlSnapshotLoader loader = new XmlSnapshotLoader();

        @Test
        @DisplayName("Загружает данные из classpath для устройства 'Line'")
        void loadForDevice_line_returnsNonEmptyMap() {
            // null baseDir → только classpath:mock-snapshots/default/Line___Unit0.xml
            Map<String, String> props = loader.loadForDevice("Line", null, "any-instance");

            assertNotNull(props, "Результат не должен быть null");
            assertFalse(props.isEmpty(), "Line___Unit0.xml должен содержать хотя бы одно свойство");
        }

        @Test
        @DisplayName("Загружает данные из classpath для всех 7 известных устройств")
        void loadForDevice_allKnownDevices_nonEmpty() {
            for (String device : XmlSnapshotLoader.KNOWN_DEVICES) {
                Map<String, String> props = loader.loadForDevice(device, null, "instance-x");
                assertFalse(props.isEmpty(),
                        "Устройство '%s': classpath-файл должен быть загружен".formatted(device));
            }
        }

        @Test
        @DisplayName("Возвращает пустую карту и не кидает исключений для несуществующего файла")
        void loadForDevice_nonExistentFile_returnsEmpty() {
            // Несуществующий базовый каталог + явно несуществующее устройство
            Map<String, String> props = loader.loadForDevice(
                    "NonExistentDevice_____",
                    "/absolutely/non/existent/path",
                    "test"
            );
            assertNotNull(props, "Метод должен вернуть non-null даже если файл не найден");
            assertTrue(props.isEmpty(), "Несуществующий файл → пустая карта");
        }

        @Test
        @DisplayName("Загруженный Line содержит ключ 'ST'")
        void loadForDevice_line_containsStKey() {
            Map<String, String> props = loader.loadForDevice("Line", null, "any");
            assertTrue(props.containsKey("ST"),
                    "Line___Unit0.xml должен содержать ключ 'ST'");
        }
    }
}
