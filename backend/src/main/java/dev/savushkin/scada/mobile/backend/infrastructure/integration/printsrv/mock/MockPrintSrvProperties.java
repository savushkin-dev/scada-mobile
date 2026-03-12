package dev.savushkin.scada.mobile.backend.infrastructure.integration.printsrv.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Конфигурация мок-реализации PrintSrv-клиентов (только в профиле {@code dev}).
 *
 * <p>Все параметры имеют разумные умолчания, поэтому {@code application-dev.yaml}
 * может переопределять лишь то, что нужно для конкретного сценария тестирования.
 *
 * <p>Активируется через {@link MockPrintSrvConfig}.
 *
 * <pre>{@code
 * printsrv:
 *   mock:
 *     snapshot-base-dir: "/opt/scada/mock-snapshots"   # опционально
 *     tick-interval-ms: 2000
 *     offline-instances: ["bosch", "grunwald11"]
 *     error-flip-probability: 0.03
 *     random-seed: 42
 *     simulation-enabled: true
 * }</pre>
 */
@ConfigurationProperties(prefix = "printsrv.mock")
public class MockPrintSrvProperties {

    /**
     * Базовая директория с XML-файлами начального состояния.
     *
     * <p>Структура: {@code {snapshotBaseDir}/{instanceId}/{DeviceName}___Unit0.xml}.
     * Если файл для инстанса не найден, используется classpath-резервная копия
     * {@code mock-snapshots/default/{DeviceName}___Unit0.xml}.
     * Если {@code null} — только classpath.
     */
    private String snapshotBaseDir = null;

    /**
     * Интервал симуляционного тика в мс. Каждый тик все non-offline инстансы
     * получают приращение счётчиков и случайное изменение состояния.
     */
    private long tickIntervalMs = 2000;

    /**
     * Instance ID-ы, которые всегда выбрасывают {@link java.io.IOException}
     * при вызове {@code queryAll}.
     *
     * <p>Используется для тестирования поведения polling-слоя при недоступности инстансов.
     */
    private List<String> offlineInstances = new ArrayList<>();

    /**
     * Вероятность появления новой ошибки (0→1) на КАЖДОМ тике ДЛЯ КАЖДОГО флага.
     *
     * <p>0.0 — ошибки никогда не появляются (по умолчанию в тестах с фиксированным seed).<br>
     * 1.0 — новая ошибка гарантированно появляется на каждом тике.
     * Применяется только если текущее число активных ошибок на аппарате
     * строго меньше {@link #maxErrorsPerUnit}.
     */
    private double errorFlipProbability = 0.03;

    /**
     * Вероятность снятия уже активной ошибки (1→0) на КАЖДОМ тике ДЛЯ КАЖДОГО флага.
     *
     * <p>Должна быть заметно выше {@link #errorFlipProbability}, чтобы ошибки
     * автоматически устранялись в разумное время. При значении 0.10 и тике 2 сек
     * ожидаемое «время жизни» одной ошибки составляет ~20 секунд.
     *
     * <p>0.0 — ошибки не снимаются сами по себе.<br>
     * 1.0 — ошибка снимается на следующем же тике.
     */
    private double errorClearProbability = 0.10;

    /**
     * Максимальное число одновременно активных ошибок (флагов равных «1»)
     * на устройстве {@code scada} одного аппарата.
     *
     * <p>Новая ошибка не появится, пока текущее число активных ошибок
     * не опустится ниже этого значения. Ошибки на {@code Line} (флаг {@code Error})
     * учитываются отдельно и этим лимитом не ограничены.
     *
     * <p>Примеры:
     * <ul>
     *   <li>1 — строгий режим: не более одной ошибки на аппарат в любой момент</li>
     *   <li>3 — умеренный (умолчание)</li>
     *   <li>10+ — для стресс-тестирования UI</li>
     * </ul>
     */
    private int maxErrorsPerUnit = 3;

    /**
     * Seed для внутреннего {@link java.util.Random}.
     *
     * <p>Фиксированный seed обеспечивает детерминированное поведение в unit-тестах:
     * при одинаковых входных данных и seed результаты воспроизводимы.
     * В продакшн-подобных dev-сессиях можно поставить {@code -1} (тогда используется
     * {@code new Random()} без seed).
     */
    private long randomSeed = 42L;

    /**
     * Если {@code false} — {@link MockStateSimulator} не запускает @Scheduled тики.
     *
     * <p>Используется в интеграционных тестах, где время управляется вручную через
     * прямые вызовы {@link MockStateSimulator#tickAll()}.
     */
    private boolean simulationEnabled = true;

    // ─── getters / setters ────────────────────────────────────────────────────

    public String getSnapshotBaseDir() { return snapshotBaseDir; }
    public void setSnapshotBaseDir(String snapshotBaseDir) { this.snapshotBaseDir = snapshotBaseDir; }

    public long getTickIntervalMs() { return tickIntervalMs; }
    public void setTickIntervalMs(long tickIntervalMs) { this.tickIntervalMs = tickIntervalMs; }

    public List<String> getOfflineInstances() { return offlineInstances; }
    public void setOfflineInstances(List<String> offlineInstances) { this.offlineInstances = offlineInstances; }

    public double getErrorFlipProbability() { return errorFlipProbability; }
    public void setErrorFlipProbability(double errorFlipProbability) {
        this.errorFlipProbability = errorFlipProbability;
    }

    public double getErrorClearProbability() {
        return errorClearProbability;
    }

    public void setErrorClearProbability(double errorClearProbability) {
        this.errorClearProbability = errorClearProbability;
    }

    public int getMaxErrorsPerUnit() {
        return maxErrorsPerUnit;
    }

    public void setMaxErrorsPerUnit(int maxErrorsPerUnit) {
        this.maxErrorsPerUnit = maxErrorsPerUnit;
    }

    public long getRandomSeed() { return randomSeed; }
    public void setRandomSeed(long randomSeed) { this.randomSeed = randomSeed; }

    public boolean isSimulationEnabled() { return simulationEnabled; }
    public void setSimulationEnabled(boolean simulationEnabled) { this.simulationEnabled = simulationEnabled; }
}
