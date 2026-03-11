package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.DevicesStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.ErrorsMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.LineStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueueMessageDTO;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties.DeviceNamesProperties;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties.InstanceProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitProperties;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final Map<String, InstanceProperties> instancesById;

    public UnitDetailService(PrintSrvProperties config, InstanceSnapshotRepository snapshotRepo) {
        this.config = config;
        this.snapshotRepo = snapshotRepo;
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
     * Строит сообщение {@code LINE_STATUS}.
     *
     * <p>Источники данных (в порядке приоритета):
     * <ul>
     *   <li>Состояние линии — устройство {@code Line}</li>
     *   <li>Данные партии — первый принтер из конфига</li>
     *   <li>Дополнительные поля — {@code BatchQueue}</li>
     * </ul>
     *
     * @param instanceId идентификатор аппарата
     * @return сообщение {@code LINE_STATUS}, или {@code null} если снапшоты ещё не получены
     */
    public @Nullable LineStatusMessageDTO buildLineStatus(String instanceId) {
        InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) return null;

        DeviceNamesProperties devices = inst.getDevices();

        Map<String, String> lineRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, devices.getLine()));
        Map<String, String> printerRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, firstPrinterName(devices)));
        Map<String, String> bqRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, devices.getBatchQueue()));

        // Выбираем значение из принтера, при отсутствии — из BatchQueue
        String lineState = getFirstUnit(snapshotRepo.get(instanceId, devices.getLine()))
                .map(u -> u.properties().getSt().orElse(null))
                .orElse(null);

        LineStatusMessageDTO.Payload payload = new LineStatusMessageDTO.Payload(
                inst.getDisplayName(),
                lineState,
                coalesce(printerRaw.get("kmc"), bqRaw.get("kmc")),
                coalesce(printerRaw.get("descr"), bqRaw.get("description")),
                coalesce(printerRaw.get("ean13"), bqRaw.get("ean13")),
                coalesce(printerRaw.get("partynumber"), bqRaw.get("batch")),
                coalesce(printerRaw.get("dateproduced"), bqRaw.get("dateproduced")),
                coalesce(printerRaw.get("datepack"), bqRaw.get("datepack")),
                coalesce(printerRaw.get("dateexpiration"), bqRaw.get("dateexpiration")),
                // initialCounter: curitem из именованного поля принтера или rawProperties
                firstUnitNamedProp(snapshotRepo.get(instanceId, firstPrinterName(devices)),
                        u -> u.properties().getCurItem().orElse(null)),
                coalesce(printerRaw.get("place"), bqRaw.get("place")),
                coalesce(printerRaw.get("itf"), bqRaw.get("itf")),
                coalesce(printerRaw.get("emk"), bqRaw.get("emk")),
                coalesce(printerRaw.get("kole"), bqRaw.get("kole")),
                coalesce(printerRaw.get("kolm"), bqRaw.get("kolm")),
                bqRaw.get("frozen"),
                bqRaw.get("region"),
                coalesce(printerRaw.get("designe"), bqRaw.get("designe")),
                coalesce(printerRaw.get("printdm"), bqRaw.get("printdm"))
        );

        return LineStatusMessageDTO.of(instanceId, nowUtc(), payload);
    }

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

        DeviceNamesProperties devices = inst.getDevices();
        Map<String, String> scadaRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, devices.getScada()));

        List<DevicesStatusMessageDTO.PrinterStatus> printers = buildPrinterStatuses(
                instanceId, devices.getPrinters(), scadaRaw);

        List<DevicesStatusMessageDTO.CameraStatus> aggregationCams = buildCameraStatuses(
                instanceId, devices.getAggregationCams(), scadaRaw);

        List<DevicesStatusMessageDTO.CameraStatus> aggregationBoxCams = buildCameraStatuses(
                instanceId, devices.getAggregationBoxCams(), scadaRaw);

        List<DevicesStatusMessageDTO.CameraStatus> checkerCams = buildCameraStatuses(
                instanceId, devices.getCheckerCams(), scadaRaw);

        DevicesStatusMessageDTO.Payload payload = new DevicesStatusMessageDTO.Payload(
                printers, aggregationCams, aggregationBoxCams, checkerCams);

        return DevicesStatusMessageDTO.of(instanceId, nowUtc(), payload);
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
     * Строит сообщение {@code ERRORS}.
     *
     * <p>Извлекает из снапшота устройства {@code scada} все свойства,
     * имена которых заканчиваются на один из суффиксов ошибок:
     * {@code Connection}, {@code Fail}, {@code Dublicate}, {@code DiffEan},
     * {@code Work}, {@code Data}, {@code Batch}, {@code Error}.
     * Суффикс разбирается эвристически — ищем совпадение в конце ключа.
     *
     * @param instanceId идентификатор аппарата
     * @return сообщение {@code ERRORS}, или {@code null} если нет снапшота scada
     */
    public @Nullable ErrorsMessageDTO buildErrorsStatus(String instanceId) {
        InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) return null;

        Map<String, String> scadaRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.getDevices().getScada()));

        List<ErrorsMessageDTO.DeviceErrorFlag> deviceErrors = scadaRaw.entrySet().stream()
                .filter(e -> isErrorFlag(e.getKey()))
                .map(e -> new ErrorsMessageDTO.DeviceErrorFlag(
                        extractObjectName(e.getKey()),
                        e.getKey(),
                        e.getValue(),
                        descriptionForKey(e.getKey())))
                .toList();

        return ErrorsMessageDTO.of(
                instanceId,
                nowUtc(),
                new ErrorsMessageDTO.Payload(deviceErrors, Collections.emptyList())
        );
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private @NonNull List<DevicesStatusMessageDTO.PrinterStatus> buildPrinterStatuses(
            String instanceId,
            List<String> printerNames,
            Map<String, String> scadaRaw
    ) {
        List<DevicesStatusMessageDTO.PrinterStatus> result = new ArrayList<>(printerNames.size());
        for (int i = 0; i < printerNames.size(); i++) {
            String printerName = printerNames.get(i);
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

            // Fallback из scada: LineDev0{N}Error → ошибка принтера по индексу
            if (state == null && !scadaRaw.isEmpty()) {
                String devNum = "%03d".formatted(11 + i * 10); // 011, 021, …
                state = scadaRaw.get("LineDev" + devNum + "Error");
            }

            result.add(new DevicesStatusMessageDTO.PrinterStatus(printerName, state, error, batch));
        }
        return result;
    }

    private @NonNull List<DevicesStatusMessageDTO.CameraStatus> buildCameraStatuses(
            String instanceId,
            List<String> camNames,
            Map<String, String> scadaRaw
    ) {
        List<DevicesStatusMessageDTO.CameraStatus> result = new ArrayList<>(camNames.size());
        for (int i = 0; i < camNames.size(); i++) {
            String camName = camNames.get(i);
            Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, camName));

            // Порядковый номер камеры определяет ключ в scada: Dev041, Dev042, …
            String devKey = "Dev%03d".formatted(41 + i);

            String read  = coalesce(camRaw.get("Total"),  scadaRaw.get(devKey + "CounterGeneral"));
            String unread = coalesce(camRaw.get("Failed"), scadaRaw.get(devKey + "CounterMissing"));
            String state = coalesce(camRaw.get("ST"),     scadaRaw.get(devKey + "Work"));
            String error = coalesce(camRaw.get("Error"),   scadaRaw.get(devKey + "Error"));

            result.add(new DevicesStatusMessageDTO.CameraStatus(camName, read, unread, state, error));
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

    /**
     * Имя первого принтера в конфиге инстанса, или {@code "Printer11"} по умолчанию.
     */
    private static @NonNull String firstPrinterName(@NonNull DeviceNamesProperties devices) {
        List<String> printers = devices.getPrinters();
        return printers.isEmpty() ? "Printer11" : printers.getFirst();
    }

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
