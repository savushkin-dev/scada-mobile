package dev.savushkin.scada.mobile.backend.services;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Вычисляет ключи (префиксы) scada-снапшота по имени устройства и его позиции в группе.
 *
 * <p>Правила маппинга (зафиксированы по анализу топологии Grunwald №11 и конфигурации YAML):
 * <ul>
 *   <li>Printer11..14 → LineDev011..014 (числовой суффикс извлекается из имени)</li>
 *   <li>aggregationCams[i] → Dev{41 + i*2}  (041, 043, 045, …)</li>
 *   <li>aggregationBoxCams[i] → Dev{42 + i*2} (042, 044, 046, …)</li>
 *   <li>CamEanChecker{N} → Dev{70 + N}       (071, 072, 073, 074)</li>
 *   <li>Обычные CamChecker* — читают поля напрямую из снапшота устройства, scada-ключ не нужен</li>
 * </ul>
 *
 * <p>Пример для Grunwald №11:
 * <pre>
 *   Printer11    → LineDev011
 *   Printer12    → LineDev012
 *   CamAgregation1    → Dev041   (i=0 → 41+0*2=41)
 *   CamAgregationBox1 → Dev042   (i=0 → 42+0*2=42)
 *   CamAgregation2    → Dev043   (i=1 → 41+1*2=43)
 *   CamAgregationBox2 → Dev044   (i=1 → 42+1*2=44)
 *   CamEanChecker1    → Dev071
 *   CamEanChecker4    → Dev074
 * </pre>
 */
public final class ScadaKeyMapper {

    private ScadaKeyMapper() {
        // utility class
    }

    /**
     * Возвращает prefix ключа scada для принтера.
     * Например: {@code "Printer11"} → {@code "LineDev011"}.
     * Поддерживает варианты нулевого заполнения (например, LineDev02 и LineDev002).
     *
     * @param printerName имя устройства (например, "Printer11")
     * @return "LineDev0NN" или {@code null}, если имя не соответствует паттерну
     */
    public static @Nullable String printerScadaPrefix(String printerName) {
        List<String> prefixes = printerScadaPrefixes(printerName);
        return prefixes.isEmpty() ? null : prefixes.getFirst();
    }

    /**
     * Возвращает все возможные prefix ключей scada для принтера.
     * Например: {@code "Printer2"} → {@code ["LineDev002", "LineDev02"]}.
     *
     * @param printerName имя устройства (например, "Printer11")
     * @return список prefix (может быть пустым, если имя не соответствует паттерну)
     */
    public static @NonNull List<String> printerScadaPrefixes(String printerName) {
        if (printerName == null || !printerName.startsWith("Printer")) {
            return List.of();
        }
        String suffix = printerName.substring("Printer".length());
        try {
            int num = Integer.parseInt(suffix);
            LinkedHashSet<String> prefixes = new LinkedHashSet<>();
            prefixes.add("LineDev%03d".formatted(num));
            prefixes.add("LineDev%02d".formatted(num));
            return List.copyOf(prefixes);
        } catch (NumberFormatException e) {
            return List.of();
        }
    }

    /**
     * Возвращает prefix ключа scada для aggregation-камеры по её 0-based индексу в списке группы.
     * <p>aggregationCams[0] → {@code "Dev041"}, [1] → {@code "Dev043"}, [2] → {@code "Dev045"}, …
     *
     * @param indexInGroup 0-based индекс камеры в списке aggregationCams
     * @return "Dev0NN" (никогда не null)
     */
    public static String aggregationCamScadaPrefix(int indexInGroup) {
        return "Dev%03d".formatted(41 + indexInGroup * 2);
    }

    /**
     * Возвращает prefix ключа scada для aggregation-box-камеры по её 0-based индексу в списке.
     * <p>aggregationBoxCams[0] → {@code "Dev042"}, [1] → {@code "Dev044"}, [2] → {@code "Dev046"}, …
     *
     * @param indexInGroup 0-based индекс камеры в списке aggregationBoxCams
     * @return "Dev0NN" (никогда не null)
     */
    public static String aggregationBoxCamScadaPrefix(int indexInGroup) {
        return "Dev%03d".formatted(42 + indexInGroup * 2);
    }

    /**
     * Проверяет, является ли камера EAN-чекером (имя начинается с {@code "CamEanChecker"}).
     * EAN-чекеры читают данные через scada-ключи Dev07X, а не напрямую из снапшота устройства.
     *
     * @param camName имя устройства
     * @return {@code true} если это EAN-чекер
     */
    public static boolean isEanChecker(@Nullable String camName) {
        return camName != null && camName.startsWith("CamEanChecker");
    }

    /**
     * Возвращает prefix ключа scada для EAN-чекера.
     * {@code "CamEanChecker1"} → {@code "Dev071"}, {@code "CamEanChecker4"} → {@code "Dev074"}.
     *
     * @param camName имя устройства
     * @return "Dev07N" или {@code null}, если число не извлечь
     */
    public static @Nullable String eanCheckerScadaPrefix(String camName) {
        if (!isEanChecker(camName)) {
            return null;
        }
        String suffix = camName.substring("CamEanChecker".length());
        try {
            int num = Integer.parseInt(suffix);
            return "Dev%03d".formatted(70 + num);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
