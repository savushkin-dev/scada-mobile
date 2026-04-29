package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.DevicesStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.ErrorsMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.LineStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueueMessageDTO;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties.InstanceProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис формирования четырёх типов WS-сообщений для канала {@code /ws/unit/{unitId}}.
 *
 * <p>Читает текущие снапшоты устройств из {@link InstanceSnapshotRepository}
 * и собирает из них сообщения:
 * <ul>
 *   <li>{@code LINE_STATUS} — состояние партии и линии (вкладка «Партия»)</li>
 *   <li>{@code DEVICES_STATUS} — состояние принтеров и камер (вкладка «Устройства»)</li>
 *   <li>{@code QUEUE} — очередь партий из {@code BatchQueue} (вкладка «Очередь»)</li>
 *   <li>{@code ERRORS} — флаги ошибок из устройства {@code scada} (вкладка «Журнал»)</li>
 * </ul>
 *
 * <p>Компонент stateless: каждый вызов читает текущее состояние store без кэширования.
 * Для одного инстанса методы могут вызываться параллельно без блокировок —
 * {@link InstanceSnapshotRepository} гарантирует thread-safety.
 */
@Service
public class UnitDetailService {

    private static final Logger log = LoggerFactory.getLogger(UnitDetailService.class);

    /**
     * Ключи свойств scada, которые являются флагами ошибок устройств.
     * Ненулевое значение (≠ "0") означает активную ошибку.
     *
     * <p>Полный набор суффиксов из {@code scada___Unit0_eval.py}:
     * <ul>
     *   <li>Connection — нет связи с устройством</li>
     *   <li>Fail — нет кодов маркировки</li>
     *   <li>Dublicate — одинаковые коды маркировки</li>
     *   <li>DiffEan — несовпадение EAN-13</li>
     *   <li>Work — нет данных с устройства</li>
     *   <li>Data — нет данных с устройства (доп.)</li>
     *   <li>Batch — не совпадает идентификатор партии</li>
     *   <li>Error — общая ошибка устройства</li>
     * </ul>
     */
    private static final Set<String> ERROR_FLAG_SUFFIXES = Set.of(
            "Connection", "Fail", "Dublicate", "DiffEan",
            "Work", "Data", "Batch", "Error"
    );

    /**
     * Человекочитаемые описания ошибок по суффиксу (из SCADA Monitor проекта).
     */
    private static final Map<String, String> ERROR_DESCRIPTIONS = Map.of(
            "Connection", "Нет связи с устройством",
            "Fail",       "Нет кодов маркировки",
            "Dublicate",  "Одинаковые коды маркировки",
            "DiffEan",    "Несовпадение EAN-13 в коде упаковки и идентификаторе партии",
            "Work",       "Нет данных с устройства",
            "Data",       "Нет данных с устройства",
            "Batch",      "Не совпадает идентификатор партии",
            "Error",      "Общая ошибка устройства"
    );

    private final PrintSrvProperties config;
    private final InstanceSnapshotRepository snapshotRepo;
    private final UnitErrorStore unitErrorStore;
    private final DeviceCompositionService deviceCompositionService;
    private final Map<String, InstanceProperties> instancesById;

    public UnitDetailService(PrintSrvProperties config,
                             InstanceSnapshotRepository snapshotRepo,
                             UnitErrorStore unitErrorStore,
                             DeviceCompositionService deviceCompositionService) {
        this.config = config;
        this.snapshotRepo = snapshotRepo;
        this.unitErrorStore = unitErrorStore;
        this.deviceCompositionService = deviceCompositionService;
        this.instancesById = config.getInstances().stream()
                .collect(Collectors.toUnmodifiableMap(
                        InstanceProperties::getId,
                        inst -> inst,
                        (left, right) -> left
                ));
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Проверяет, существует ли инстанс с данным ID в конфигурации.
     *
     * @param instanceId идентификатор аппарата
     * @return {@code true}, если инстанс зарегистрирован в YAML
     */
    public boolean isKnownInstance(String instanceId) {
        return instancesById.containsKey(instanceId);
    }

    /**
     * Строит статус камеры, используя как device-поля, так и scada-ключ.
     */
    private static DevicesStatusMessageDTO.CameraStatus buildSingleCamStatus(
            String camName,
            Map<String, String> camRaw,
            String devKey,
            Map<String, String> scadaRaw
    ) {
        String read = coalesce(camRaw.get("Total"), scadaRaw.get(devKey + "CounterGeneral"));
        String unread = coalesce(camRaw.get("Failed"), scadaRaw.get(devKey + "CounterMissing"));
        String state = coalesce(camRaw.get("ST"), scadaRaw.get(devKey + "Work"));
        String error = coalesce(camRaw.get("Error"), scadaRaw.get(devKey + "Error"));
        return new DevicesStatusMessageDTO.CameraStatus(camName, read, unread, state, error);
    }

    /**
     * Строит статус камеры только из прямых device-полей снапшота (без scada).
     */
    private static DevicesStatusMessageDTO.CameraStatus buildSingleCamStatusDirect(
            String camName,
            Map<String, String> camRaw
    ) {
        return new DevicesStatusMessageDTO.CameraStatus(
                camName,
                camRaw.get("Total"),
                camRaw.get("Failed"),
                camRaw.get("ST"),
                camRaw.get("Error")
        );
    }

    /**
     * Строит сообщение {@code QUEUE}.
     *
     * <p>Разбирает позиции {@code Item01}–{@code Item10} устройства {@code BatchQueue}.
     * Формат строки: {@code "Описание | номер партии | дата выработки"}.
     * Позиции «Пусто» или пустые строки опускаются.
     *
     * @param instanceId идентификатор аппарата
     * @return сообщение {@code QUEUE}, или {@code null} если нет снапшота BatchQueue
     */
    public @Nullable QueueMessageDTO buildQueueStatus(String instanceId) {
        InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) return null;

        Map<String, String> bqRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.getDevices().getBatchQueue()));

        List<QueueMessageDTO.Item> items = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String key = "Item%02d".formatted(i);
            String raw = bqRaw.get(key);
            if (raw == null || raw.isBlank() || "Пусто".equalsIgnoreCase(raw.trim())) {
                continue;
            }
            items.add(parseQueueItem(i, raw));
        }

        return QueueMessageDTO.of(instanceId, nowUtc(), new QueueMessageDTO.Payload(items));
    }

    /**
     * Строит сообщение {@code LINE_STATUS}.
     *
     * <p>Источники данных (в порядке приоритета):
     * <ul>
     *   <li>Состояние линии ({@code lineState}) — устройство {@code Line}, поле {@code ST}</li>
     *   <li>Данные партии — <b>BatchQueue-first</b>; принтер используется как fallback</li>
     *   <li>{@code initialCounter} — поле {@code curItem} первого принтера (семантика уточняется)</li>
     * </ul>
     *
     * @param instanceId идентификатор аппарата
     * @return сообщение {@code LINE_STATUS}, или {@code null} если снапшоты ещё не получены
     */
    public @Nullable LineStatusMessageDTO buildLineStatus(String instanceId) {
        InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) return null;

        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        String firstPrinter = composition.printers().isEmpty() ? "Printer11" : composition.printers().getFirst();

        Map<String, String> printerRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, firstPrinter));
        Map<String, String> bqRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.getDevices().getBatchQueue()));

        String lineState = getFirstUnit(snapshotRepo.get(instanceId, inst.getDevices().getLine()))
                .map(u -> u.properties().getSt().orElse(null))
                .orElse(null);

        // BatchQueue-first: BQ является основным источником данных партии;
        // принтер используется только как fallback когда BQ не содержит поля.
        LineStatusMessageDTO.Payload payload = new LineStatusMessageDTO.Payload(
                inst.getDisplayName(),
                lineState,
                coalesce(bqRaw.get("kmc"), printerRaw.get("kmc")),
                coalesce(bqRaw.get("description"), printerRaw.get("descr")),
                coalesce(bqRaw.get("ean13"), printerRaw.get("ean13")),
                coalesce(bqRaw.get("batch"), printerRaw.get("partynumber")),
                coalesce(bqRaw.get("dateproduced"), printerRaw.get("dateproduced")),
                coalesce(bqRaw.get("datepack"), printerRaw.get("datepack")),
                coalesce(bqRaw.get("dateexpiration"), printerRaw.get("dateexpiration")),
                // initialCounter: curItem из первого принтера (семантика уточняется отдельно)
                firstUnitNamedProp(snapshotRepo.get(instanceId, firstPrinter),
                        u -> u.properties().getCurItem().orElse(null)),
                coalesce(bqRaw.get("place"), printerRaw.get("place")),
                coalesce(bqRaw.get("itf"), printerRaw.get("itf")),
                coalesce(bqRaw.get("emk"), printerRaw.get("emk")),
                coalesce(bqRaw.get("kole"), printerRaw.get("kole")),
                coalesce(bqRaw.get("kolm"), printerRaw.get("kolm")),
                bqRaw.get("frozen"),
                bqRaw.get("region"),
                coalesce(bqRaw.get("designe"), printerRaw.get("designe")),
                coalesce(bqRaw.get("printdm"), printerRaw.get("printdm"))
        );

        return LineStatusMessageDTO.of(instanceId, nowUtc(), payload);
    }

    /**
     * Строит сообщение {@code ERRORS}.
     *
     * <p>Читает активные ошибки из {@code UnitErrorStore} — единственного источника правды.
     * Все записи store уже прошли фильтрацию активности при записи (поле {@code value="1"}).
     * Если store для данного аппарата пуст — отправляется пустой список ошибок.
     *
     * @param instanceId идентификатор аппарата
     * @return сообщение {@code ERRORS}, или {@code null} если аппарат неизвестен
     */
    public @Nullable ErrorsMessageDTO buildErrorsStatus(String instanceId) {
        if (!instancesById.containsKey(instanceId)) return null;

        List<ErrorsMessageDTO.DeviceErrorFlag> deviceErrors = unitErrorStore.getErrors(instanceId)
                .stream()
                .map(e -> new ErrorsMessageDTO.DeviceErrorFlag(
                        e.objectName(),
                        e.propertyDesc(),
                        "1",
                        e.description()))
                .toList();

        return ErrorsMessageDTO.of(
                instanceId,
                nowUtc(),
                new ErrorsMessageDTO.Payload(deviceErrors, Collections.emptyList())
        );
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Строит сообщение {@code DEVICES_STATUS}.
     *
     * <p>Для каждого устройства извлекается снапшот из store.
     * Состояние камер берётся из собственного снапшота камеры (rawProperties)
     * или из устройства {@code scada} по ключу {@code Dev0XX*} — в качестве fallback.
     *
     * @param instanceId идентификатор аппарата
     * @return сообщение {@code DEVICES_STATUS}, или {@code null} если нет снапшотов
     */
    public @Nullable DevicesStatusMessageDTO buildDevicesStatus(String instanceId) {
        InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) return null;

        Map<String, String> scadaRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.getDevices().getScada()));

        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);

        List<DevicesStatusMessageDTO.PrinterStatus> printers = buildPrinterStatuses(
                instanceId, composition.printers(), scadaRaw);

        List<DevicesStatusMessageDTO.CameraStatus> aggregationCams = buildAggregationCamStatuses(
                instanceId, composition.aggregationCams(), scadaRaw);

        List<DevicesStatusMessageDTO.CameraStatus> aggregationBoxCams = buildAggregationBoxCamStatuses(
                instanceId, composition.aggregationBoxCams(), scadaRaw);

        List<DevicesStatusMessageDTO.CameraStatus> checkerCams = buildCheckerCamStatuses(
                instanceId, composition.checkerCams(), scadaRaw);

        DevicesStatusMessageDTO.Payload payload = new DevicesStatusMessageDTO.Payload(
                printers, aggregationCams, aggregationBoxCams, checkerCams);

        return DevicesStatusMessageDTO.of(instanceId, nowUtc(), payload);
    }

    /**
     * Извлекает список <b>активных</b> ошибок устройств данного инстанса.
     *
     * <p>Источник — только scada-флаги {@code DevXXXSuffix} / {@code LineDevXXXSuffix}.
     * Ошибки фильтруются по фактическому составу устройств аппарата
     * (printers + cams из {@link DeviceCompositionService}).
     *
     * <p>Результат предназначен для записи в {@code UnitErrorStore}; используется
     * {@code buildErrorsStatus} и {@code AlertService} как единый источник правды.
     *
     * @param instanceId идентификатор аппарата
     * @return неизменяемый список активных ошибок (пустой, если ошибок нет)
     */
    public @NonNull List<DeviceError> extractActiveErrors(String instanceId) {
        InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) return List.of();

        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        List<String> allowedPrefixes = buildErrorDevicePrefixes(composition);
        if (allowedPrefixes.isEmpty()) {
            return List.of();
        }

        Map<String, List<DeviceError>> errorsByDevice = new LinkedHashMap<>();
        for (String prefix : allowedPrefixes) {
            errorsByDevice.put(prefix, new ArrayList<>());
        }

        Map<String, String> scadaRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.getDevices().getScada()));

        for (Map.Entry<String, String> entry : scadaRaw.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!isErrorFlag(key) || !isActiveErrorValue(value)) {
                continue;
            }
            String objectName = extractObjectName(key);
            List<DeviceError> bucket = errorsByDevice.get(objectName);
            if (bucket == null) {
                continue; // ignore errors for devices outside composition
            }
            bucket.add(new DeviceError(objectName, key, descriptionForKey(key)));
        }

        List<DeviceError> errors = new ArrayList<>();
        for (List<DeviceError> bucket : errorsByDevice.values()) {
            errors.addAll(bucket);
        }
        return List.copyOf(errors);
    }

    private @NonNull List<DevicesStatusMessageDTO.PrinterStatus> buildPrinterStatuses(
            String instanceId,
            List<String> printerNames,
            Map<String, String> scadaRaw
    ) {
        List<DevicesStatusMessageDTO.PrinterStatus> result = new ArrayList<>(printerNames.size());
        for (String printerName : printerNames) {
            DeviceSnapshot snap = snapshotRepo.get(instanceId, printerName);

            String state = getFirstUnit(snap)
                    .map(u -> u.properties().getSt().orElse(null))
                    .orElse(null);
            String error = getFirstUnit(snap)
                    .map(u -> u.properties().getError().orElse(null))
                    .orElse(null);
            String batch = getFirstUnit(snap)
                    .map(u -> u.properties().getCurItem().orElse(null))
                    .orElse(null);

            // Fallback из scada: LineDev0{NN}Error → ошибка принтера по имени устройства
            // Пример: Printer11 → LineDev011Error, Printer12 → LineDev012Error
            if (error == null && !scadaRaw.isEmpty()) {
                for (String scadaPrefix : ScadaKeyMapper.printerScadaPrefixes(printerName)) {
                    error = scadaRaw.get(scadaPrefix + "Error");
                    if (error != null) {
                        break;
                    }
                }
            }

            result.add(new DevicesStatusMessageDTO.PrinterStatus(printerName, state, error, batch));
        }
        return result;
    }

    /**
     * Строит статусы aggregation-камер.
     * scada-ключ для группы aggregationCams[i]: Dev{41 + i*2} (041, 043, 045, …).
     */
    private @NonNull List<DevicesStatusMessageDTO.CameraStatus> buildAggregationCamStatuses(
            String instanceId,
            List<String> camNames,
            Map<String, String> scadaRaw
    ) {
        List<DevicesStatusMessageDTO.CameraStatus> result = new ArrayList<>(camNames.size());
        for (int i = 0; i < camNames.size(); i++) {
            String camName = camNames.get(i);
            Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, camName));
            String devKey = ScadaKeyMapper.aggregationCamScadaPrefix(i);
            result.add(buildSingleCamStatus(camName, camRaw, devKey, scadaRaw));
        }
        return result;
    }

    /**
     * Строит статусы aggregation-box-камер.
     * scada-ключ для группы aggregationBoxCams[i]: Dev{42 + i*2} (042, 044, 046, …).
     */
    private @NonNull List<DevicesStatusMessageDTO.CameraStatus> buildAggregationBoxCamStatuses(
            String instanceId,
            List<String> camNames,
            Map<String, String> scadaRaw
    ) {
        List<DevicesStatusMessageDTO.CameraStatus> result = new ArrayList<>(camNames.size());
        for (int i = 0; i < camNames.size(); i++) {
            String camName = camNames.get(i);
            Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, camName));
            String devKey = ScadaKeyMapper.aggregationBoxCamScadaPrefix(i);
            result.add(buildSingleCamStatus(camName, camRaw, devKey, scadaRaw));
        }
        return result;
    }

    /**
     * Строит статусы checker-камер (обычные + EAN-чекеры).
     * <ul>
     *   <li>CamEanChecker{N} — читает через scada Dev{70+N} (071..074) с fallback на device-поля.</li>
     *   <li>CamChecker*, CamBatch, CamPacker и прочие — читают напрямую из снапшота устройства.</li>
     * </ul>
     */
    private @NonNull List<DevicesStatusMessageDTO.CameraStatus> buildCheckerCamStatuses(
            String instanceId,
            List<String> camNames,
            Map<String, String> scadaRaw
    ) {
        List<DevicesStatusMessageDTO.CameraStatus> result = new ArrayList<>(camNames.size());
        for (String camName : camNames) {
            Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, camName));
            if (ScadaKeyMapper.isEanChecker(camName)) {
                String devKey = ScadaKeyMapper.eanCheckerScadaPrefix(camName);
                if (devKey != null) {
                    result.add(buildSingleCamStatus(camName, camRaw, devKey, scadaRaw));
                } else {
                    // Не удалось распознать номер — читаем только device-поля
                    result.add(buildSingleCamStatusDirect(camName, camRaw));
                    log.warn("[{}] Cannot derive scada key for EAN checker: {}", instanceId, camName);
                }
            } else {
                // Обычный checker (CamChecker, CamBatch, CamPacker, …)
                // читает поля Total/Failed/ST/Error напрямую из снапшота устройства
                result.add(buildSingleCamStatusDirect(camName, camRaw));
            }
        }
        return result;
    }

    /**
     * Разбирает строку очереди формата {@code "Описание | batch | дата"}.
     * Поля разделены {@code " | "} (пробел-вертикальная черта-пробел).
     * Если строка не содержит разделителей — помещается в shortCode целиком.
     */
    private static QueueMessageDTO.Item parseQueueItem(int position, String raw) {
        String[] parts = raw.split(" \\| ", 3);
        return new QueueMessageDTO.Item(
                position,
                parts.length > 0 ? nullIfBlank(parts[0]) : null,
                parts.length > 1 ? nullIfBlank(parts[1]) : null,
                parts.length > 2 ? nullIfBlank(parts[2]) : null
        );
    }

    /**
     * Проверяет, является ли ключ свойства scada флагом ошибки устройства.
     * Имена вида {@code DevXXXFail}, {@code DevXXXDublicate}, {@code DevXXXError}, …
     */
    @SuppressWarnings("java:S3776") // Читаемость важнее цикломатической сложности
    private static boolean isErrorFlag(String key) {
        for (String suffix : ERROR_FLAG_SUFFIXES) {
            if (key.endsWith(suffix) && key.length() > suffix.length()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Извлекает «имя объекта» из ключа scada: {@code Dev041Dublicate} → {@code Dev041}.
     */
    private static @NonNull String extractObjectName(String key) {
        for (String suffix : ERROR_FLAG_SUFFIXES) {
            if (key.endsWith(suffix)) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return key;
    }

    /**
     * Возвращает описание ошибки по ключу scada: {@code Dev041Fail} → {@code "Нет кодов маркировки"}.
     */
    private static @NonNull String descriptionForKey(String key) {
        for (String suffix : ERROR_FLAG_SUFFIXES) {
            if (key.endsWith(suffix)) {
                return ERROR_DESCRIPTIONS.getOrDefault(suffix, suffix);
            }
        }
        return key;
    }

    private static boolean isActiveErrorValue(@Nullable String value) {
        return value != null && !value.isBlank() && !"0".equals(value);
    }

    private static @NonNull List<String> buildErrorDevicePrefixes(DeviceComposition composition) {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();

        for (String printer : composition.printers()) {
            List<String> printerPrefixes = ScadaKeyMapper.printerScadaPrefixes(printer);
            if (printerPrefixes.isEmpty()) {
                prefixes.add(printer);
            } else {
                prefixes.addAll(printerPrefixes);
            }
        }

        for (int i = 0; i < composition.aggregationCams().size(); i++) {
            prefixes.add(ScadaKeyMapper.aggregationCamScadaPrefix(i));
        }

        for (int i = 0; i < composition.aggregationBoxCams().size(); i++) {
            prefixes.add(ScadaKeyMapper.aggregationBoxCamScadaPrefix(i));
        }

        for (String camName : composition.checkerCams()) {
            if (ScadaKeyMapper.isEanChecker(camName)) {
                String devKey = ScadaKeyMapper.eanCheckerScadaPrefix(camName);
                if (devKey != null) {
                    prefixes.add(devKey);
                } else {
                    prefixes.add(camName);
                }
            } else {
                prefixes.add(camName);
            }
        }

        return List.copyOf(prefixes);
    }

    /**
     * Возвращает rawProperties первого юнита снапшота устройства,
     * или пустую карту если снапшот отсутствует.
     */
    private static @NonNull Map<String, String> firstUnitRawProperties(@Nullable DeviceSnapshot snapshot) {
        if (snapshot == null || snapshot.units().isEmpty()) {
            return Collections.emptyMap();
        }
        return snapshot.units().values().iterator().next().properties().getRawProperties();
    }

    /**
     * Возвращает Optional первого юнита снапшота, или пустой Optional.
     */
    private static @NonNull Optional<UnitSnapshot> getFirstUnit(@Nullable DeviceSnapshot snapshot) {
        if (snapshot == null || snapshot.units().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snapshot.units().values().iterator().next());
    }

    /**
     * Извлекает именованное поле из первого юнита снапшота.
     */
    private static @Nullable String firstUnitNamedProp(
            @Nullable DeviceSnapshot snapshot,
            java.util.function.Function<UnitSnapshot, @Nullable String> extractor
    ) {
        return getFirstUnit(snapshot).map(extractor).orElse(null);
    }

    // firstPrinterName заменён на использование DeviceCompositionService.getComposition()

    /** Возвращает первое ненулевое, непустое значение из аргументов. */
    @SafeVarargs
    private static @Nullable String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static @Nullable String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static @NonNull String nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
