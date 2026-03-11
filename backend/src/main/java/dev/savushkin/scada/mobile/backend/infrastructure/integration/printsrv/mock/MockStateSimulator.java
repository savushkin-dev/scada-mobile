package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Генератор симулированных изменений состояния для всех мок-инстансов PrintSrv.
 *
 * <h3>Что симулируется</h3>
 * <ul>
 *   <li><b>Камеры агрегации</b> — непрерывно набирают {@code Total}
 *       и {@code Succeeded}; с вероятностью {@code errorFlipProbability} появляются
 *       {@code Failed} и {@code BatchFailed}.</li>
 *   <li><b>Line</b> — если {@code ST=1} (активна), счётчик в {@code CurItem} растёт;
 *       с той же вероятностью устанавливается / снимается флаг {@code Error=1}.</li>
 *   <li><b>Принтеры</b> — если {@code ST=1}, инкрементируется {@code CurItem}.</li>
 *   <li><b>SCADA</b> — агрегированные булевые (0/1) флаги ошибок устройств
 *       ({@code Dev041Connection}, {@code Dev041Fail}, …, {@code LineDev011Error})
 *       на устройстве {@code scada}, по логике {@code scada___Unit0_eval.py}.</li>
 * </ul>
 *
 * <h3>Детерминированность при тестировании</h3>
 * Конструктор принимает {@code randomSeed} из {@link MockPrintSrvProperties};
 * при фиксированном seed результаты каждого вызова {@link #tickAll()} идентичны.
 * Это позволяет юнит-тестам точно предсказывать состояние через N тиков.
 *
 * <h3>Потокобезопасность</h3>
 * {@code @Scheduled} выполняется в одном потоке Spring Scheduler.
 * Все записи идут через {@link MockInstanceState}, у которого write-lock.
 * Метод {@link #tickAll()} является единственной точкой мутации.
 */
@Component
@Profile("dev")
public class MockStateSimulator {

    private static final Logger log = LoggerFactory.getLogger(MockStateSimulator.class);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MockPrintSrvClientRegistry registry;
    private final MockPrintSrvProperties mockProperties;
    private final Map<String, PrintSrvProperties.InstanceProperties> instancesById;
    private final Random random;

    public MockStateSimulator(
            MockPrintSrvClientRegistry registry,
            MockPrintSrvProperties mockProperties,
            PrintSrvProperties printSrvProperties
    ) {
        this.registry = registry;
        this.mockProperties = mockProperties;
        this.instancesById = printSrvProperties.getInstances().stream()
                .collect(LinkedHashMap::new,
                        (map, inst) -> map.put(inst.getId(), inst),
                        Map::putAll);
        this.random = new Random(mockProperties.getRandomSeed());
    }

    // ─── Плановый тик ──────────────────────────────────────────────────────

    /**
     * Scheduled-задача: вызывается Spring-планировщиком согласно конфигурации.
     *
     * <p>{@code fixedDelay} — задержка ПОСЛЕ завершения предыдущего тика.
     * При долгой работе инстансов это не накапливает очередь задач.
     */
    @Scheduled(fixedDelayString = "${printsrv.mock.tick-interval-ms:2000}")
    public void tick() {
        if (!mockProperties.isSimulationEnabled()) {
            return;
        }
        tickAll();
    }

    /**
     * Выполняет один тик симуляции для всех онлайн-инстансов.
     *
     * <p>Публичный метод открыт для прямого вызова в unit-тестах без запуска
     * планировщика. Каждый вызов использует один и тот же {@link Random}-объект,
     * поэтому последовательность при фиксированном seed детерминирована.
     */
    public void tickAll() {
        for (MockPrintSrvClient client : registry.getAllMock()) {
            if (client.isOffline()) {
                continue;
            }
            try {
                tickInstance(client);
            } catch (Exception ex) {
                // Не прерываем весь цикл из-за одного неудачного инстанса
                log.error("[{}] Simulator tick failed: {}", client.getInstanceId(), ex.getMessage(), ex);
            }
        }
    }

    // ─── Симуляция одного инстанса ──────────────────────────────────────────

    private void tickInstance(MockPrintSrvClient client) {
        MockInstanceState state = client.getState();
        String id = client.getInstanceId();
        PrintSrvProperties.InstanceProperties inst = instancesById.get(id);
        if (inst == null) {
            log.debug("[{}] Simulator: instance config not found, skipping", id);
            return;
        }

        tickLine(state, inst.getDevices().getLine(), id);

        for (String device : inst.getDevices().getAggregationCams()) {
            tickCamAggregation(state, device, id);
        }
        for (String device : inst.getDevices().getAggregationBoxCams()) {
            tickCamAggregation(state, device, id);
        }
        for (String device : inst.getDevices().getPrinters()) {
            tickPrinter(state, device, id);
        }

        tickScada(state, inst, id);
    }

    // ─── CamAgregation / CamAgregationBox ──────────────────────────────────

    /**
     * Симулирует работу камеры агрегации.
     *
     * <p>За один тик камера «читает» от 1 до 5 пачек.
     * С вероятностью {@code errorFlipProbability} одна из них «бракуется».
     */
    private void tickCamAggregation(MockInstanceState state, String device, String instanceId) {
        Map<String, String> props = state.getPropertiesCopy(device);
        if (props.isEmpty()) {
            return; // устройство не инициализировано для данного инстанса
        }

        // Только если устройство активно
        String st = props.getOrDefault("ST", "0");
        if (!"1".equals(st)) {
            return;
        }

        int increment = 1 + random.nextInt(5); // 1-5 пачек за тик
        state.incrementInt(device, "Total", increment);
        state.incrementInt(device, "Succeeded", increment);

        // Брак с заданной вероятностью
        if (flipProbability()) {
            int failed = 1 + random.nextInt(2);
            state.incrementInt(device, "Failed", failed);
            state.incrementInt(device, "BatchFailed", 1);
            log.trace("[{}] {} — {} failed codes this tick", instanceId, device, failed);
        }
    }

    // ─── Line ──────────────────────────────────────────────────────────────

    /**
     * Симулирует активную линию маркировки.
     *
     * <p>Если {@code ST=1}: инкрементируется счётчик в {@code CurItem},
     * обновляется {@code LastReadTime}.
     * С вероятностью {@code errorFlipProbability} флаг {@code Error} инвертируется.
     */
    private void tickLine(MockInstanceState state, String device, String instanceId) {
        Map<String, String> props = state.getPropertiesCopy(device);
        if (props.isEmpty()) {
            return;
        }

        // Ошибочный флаг инвертируем независимо от ST (ошибка может прийти когда угодно)
        if (flipProbability()) {
            String currentError = props.getOrDefault("Error", "0");
            String newError = "1".equals(currentError) ? "0" : "1";
            state.setProperty(device, "Error", newError);
            log.debug("[{}] {} — Error flipped to {}", instanceId, device, newError);
        }

        String st = props.getOrDefault("ST", "0");
        if (!"1".equals(st)) {
            return;
        }

        // Инкрементируем первый числовой токен в CurItem
        String curItem = props.getOrDefault("CurItem", "");
        String updatedCurItem = incrementCurItemCounter(curItem);
        state.setProperty(device, "CurItem", updatedCurItem);

        // Обновляем время последнего чтения (HH:mm:ss)
        state.setProperty(device, "LastReadTime", LocalTime.now().format(TIME_FMT));
    }

    // ─── Printers ──────────────────────────────────────────────────────────

    /**
     * Симулирует принтер маркировки: если активен — инкрементирует CurItem.
     */
    private void tickPrinter(MockInstanceState state, String device, String instanceId) {
        Map<String, String> props = state.getPropertiesCopy(device);
        if (props.isEmpty()) {
            return;
        }

        String st = props.getOrDefault("ST", "0");
        if (!"1".equals(st)) {
            return;
        }

        String curItem = props.getOrDefault("CurItem", "");
        String updated = incrementCurItemCounter(curItem);
        state.setProperty(device, "CurItem", updated);
    }

    // ─── Scada (агрегированные флаги ошибок устройств) ─────────────────────

    /**
     * Суффиксы ошибок камер (devarr-устройства) — соответствуют {@code device.err*}
     * свойствам из реального {@code scada___Unit0_eval.py}.
     */
    private static final List<String> CAM_ERROR_SUFFIXES = List.of(
            "Connection", "Fail", "Dublicate", "DiffEan", "Work", "Data", "Batch", "Error"
    );

    /**
     * Суффиксы ошибок принтеров/PLC (linedevarr-устройства).
     */
    private static final List<String> LINE_DEV_ERROR_SUFFIXES = List.of(
            "Connection", "Error"
    );

    /**
     * Симулирует обновление устройства {@code scada} —
     * булевые (0/1) флаги ошибок для камер и принтеров.
     *
     * <p>Логика повторяет реальный {@code scada___Unit0_eval.py}:
     * для каждого устройства из {@code devarr} (камеры, нумерация Dev041, Dev042, …)
     * и {@code linedevarr} (принтеры, нумерация LineDev011, LineDev021, …)
     * с вероятностью {@code errorFlipProbability} инвертируется каждый флаг,
     * после чего {@code lineerr} вычисляется как OR всех {@code *Error} флагов.
     */
    private void tickScada(
            MockInstanceState state,
            PrintSrvProperties.InstanceProperties inst,
            String instanceId
    ) {
        String scadaDevice = inst.getDevices().getScada();
        Map<String, String> scadaProps = state.getPropertiesCopy(scadaDevice);
        if (scadaProps.isEmpty()) {
            return;
        }

        boolean lineErr = false;

        // Камеры агрегации → Dev041, Dev042, ...
        List<String> allCams = new java.util.ArrayList<>();
        allCams.addAll(inst.getDevices().getAggregationCams());
        allCams.addAll(inst.getDevices().getAggregationBoxCams());
        for (int i = 0; i < allCams.size(); i++) {
            String devPrefix = "Dev%03d".formatted(41 + i);
            lineErr |= tickScadaErrorFlags(state, scadaDevice, scadaProps, devPrefix,
                    CAM_ERROR_SUFFIXES, instanceId);
        }

        // Принтеры → LineDev011, LineDev021, ...
        List<String> printers = inst.getDevices().getPrinters();
        for (int i = 0; i < printers.size(); i++) {
            String devPrefix = "LineDev%03d".formatted(11 + i * 10);
            lineErr |= tickScadaErrorFlags(state, scadaDevice, scadaProps, devPrefix,
                    LINE_DEV_ERROR_SUFFIXES, instanceId);
        }

        state.setProperty(scadaDevice, "lineerr", lineErr ? "1" : "0");
    }

    /**
     * Обрабатывает одну группу error-флагов ({@code devPrefix + suffix})
     * на устройстве {@code scada}: с вероятностью {@code errorFlipProbability}
     * инвертирует каждый булев флаг.
     *
     * @return {@code true}, если хотя бы один {@code *Error} флаг сейчас равен "1"
     */
    private boolean tickScadaErrorFlags(
            MockInstanceState state,
            String scadaDevice,
            Map<String, String> scadaProps,
            String devPrefix,
            List<String> suffixes,
            String instanceId
    ) {
        boolean hasError = false;
        for (String suffix : suffixes) {
            String key = devPrefix + suffix;
            String current = scadaProps.getOrDefault(key, "0");
            if (flipProbability()) {
                String flipped = "1".equals(current) ? "0" : "1";
                state.setProperty(scadaDevice, key, flipped);
                log.trace("[{}] scada.{} flipped {} → {}", instanceId, key, current, flipped);
                current = flipped;
            }
            if ("Error".equals(suffix)) {
                hasError = "1".equals(current);
            }
        }
        return hasError;
    }

    // ─── Внутренние хелперы ─────────────────────────────────────────────────

    /**
     * Возвращает {@code true} с вероятностью {@link MockPrintSrvProperties#getErrorFlipProbability()}.
     */
    private boolean flipProbability() {
        return random.nextDouble() < mockProperties.getErrorFlipProbability();
    }

    /**
     * Инкрементирует первый числовой токен в строке формата {@code "1605 | 147 | 19.08.2025"}.
     *
     * <p>Если строка не соответствует формату («»*, нет цифр в начале, etc.),
     * возвращает исходную строку без изменений.
     *
     * <p>Примеры:
     * <pre>
     *   "1605 | 147 | 19.08.2025"  →  "1606 | 147 | 19.08.2025"
     *   ""                          →  ""
     *   "abc | 10"                  →  "abc | 10"  (без изменений)
     *   "42"                        →  "43"
     * </pre>
     *
     * @param curItem текущее значение CurItem
     * @return обновлённое значение CurItem
     */
    static String incrementCurItemCounter(String curItem) {
        if (curItem == null || curItem.isBlank()) {
            return curItem == null ? "" : curItem;
        }
        int pipeIndex = curItem.indexOf('|');
        String firstPart = pipeIndex >= 0 ? curItem.substring(0, pipeIndex).trim() : curItem.trim();
        String rest      = pipeIndex >= 0 ? curItem.substring(pipeIndex) : "";

        try {
            int counter = Integer.parseInt(firstPart);
            String newFirst = String.valueOf(counter + 1);
            return pipeIndex >= 0 ? newFirst + " " + rest : newFirst;
        } catch (NumberFormatException e) {
            return curItem; // не трогаем нераспознанный формат
        }
    }
}
