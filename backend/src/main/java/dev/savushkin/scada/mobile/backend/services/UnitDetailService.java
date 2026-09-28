package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.DevicesStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.ErrorsMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.LineStatusMessageDTO;
import dev.savushkin.scada.mobile.backend.api.dto.QueueMessageDTO;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
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

    private final PrintSrvTopologyRepository topologyRepo;
    private final InstanceSnapshotRepository snapshotRepo;
    private final UnitErrorStore unitErrorStore;
    private final DeviceCompositionService deviceCompositionService;
    private final DeviceScadaRegistry deviceScadaRegistry;
    private final DeviceCounterResolver deviceCounterResolver;
    private final DeviceGroupService deviceGroupService;

    public UnitDetailService(PrintSrvTopologyRepository topologyRepo,
                             InstanceSnapshotRepository snapshotRepo,
                             UnitErrorStore unitErrorStore,
                             DeviceCompositionService deviceCompositionService,
                             DeviceScadaRegistry deviceScadaRegistry,
                             DeviceCounterResolver deviceCounterResolver,
                             DeviceGroupService deviceGroupService) {
        this.topologyRepo = topologyRepo;
        this.snapshotRepo = snapshotRepo;
        this.unitErrorStore = unitErrorStore;
        this.deviceCompositionService = deviceCompositionService;
        this.deviceScadaRegistry = deviceScadaRegistry;
        this.deviceCounterResolver = deviceCounterResolver;
        this.deviceGroupService = deviceGroupService;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Проверяет, существует ли инстанс с данным ID в БД.
     *
     * @param instanceId идентификатор аппарата
     * @return {@code true}, если инстанс зарегистрирован в БД
     */
    public boolean isKnownInstance(String instanceId) {
        return topologyRepo.findByInstanceId(instanceId).isPresent();
    }

    /**
     * Строит статус камеры, используя как device-поля, так и scada-ключ.
     * Поле {@code st} берётся из поля {@code ST} снапшота устройства
     * (0 — остановлено, 1 — работает); флаг ошибки — отдельное поле {@code Error}.
     */
    private static DevicesStatusMessageDTO.CameraStatus buildSingleCamStatus(
            String camName,
            Map<String, String> camRaw,
            String devKey,
            Map<String, String> scadaRaw
    ) {
        RuntimeTagMapper.CounterResolution counters = RuntimeTagMapper.resolveCounters(camRaw, scadaRaw, devKey);
        String read = counters.read();
        String unread = counters.unread();
        String st = coalesce(camRaw.get("ST"), scadaRaw.get(devKey + "ST"));
        String error = coalesce(
            camRaw.get("Error"),
            scadaRaw.get(devKey + "Error")
        );
        if (RuntimeTagMapper.hasActiveError(scadaRaw, devKey)) {
            error = "1";
        }
        String batch = camRaw.get("curitem");
        return new DevicesStatusMessageDTO.CameraStatus(camName, read, unread, st, error, batch, false);
    }

    /**
     * Строит статус камеры только из прямых device-полей снапшота (без scada).
     * Поле {@code st} берётся из поля {@code ST} снапшота устройства.
     */
    private static DevicesStatusMessageDTO.CameraStatus buildSingleCamStatusDirect(
            String camName,
            Map<String, String> camRaw
    ) {
        return new DevicesStatusMessageDTO.CameraStatus(
                camName,
                camRaw.get("Succeeded"),
                camRaw.get("Failed"),
                camRaw.get("ST"),
                camRaw.get("Error"),
                camRaw.get("curitem"),
                false
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
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) return null;

        Map<String, String> bqRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.batchQueueDeviceName()));

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
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) return null;

        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        String firstPrinter = composition.printers().isEmpty() ? "Printer11" : composition.printers().getFirst();

        Map<String, String> printerRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, firstPrinter));
        Map<String, String> bqRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.batchQueueDeviceName()));

        String lineState = getFirstUnit(snapshotRepo.get(instanceId, inst.lineDeviceName()))
                .map(u -> u.properties().getSt().orElse(null))
                .orElse(null);

        // BatchQueue-first: BQ является основным источником данных партии;
        // принтер используется только как fallback когда BQ не содержит поля.

        // Счётчики камер для карточки аппарата — единый агрегат DeviceCounterResolver
        // (show_counters-камеры по display_order, fallback — камеры агрегации).
        DeviceCounterResolver.UnitCounters cameraCounters = deviceCounterResolver.resolveUnitCounters(instanceId);
        String cameraRead = cameraCounters.read();
        String cameraUnread = cameraCounters.unread();

        LineStatusMessageDTO.Payload payload = new LineStatusMessageDTO.Payload(
                inst.displayName(),
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
                coalesce(bqRaw.get("printdm"), printerRaw.get("printdm")),
                cameraRead,
                cameraUnread
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
        if (!isKnownInstance(instanceId)) return null;

        List<ErrorsMessageDTO.DeviceErrorFlag> deviceErrors = unitErrorStore.getErrors(instanceId)
                .stream()
                .map(e -> new ErrorsMessageDTO.DeviceErrorFlag(
                        e.objectName(),
                        e.propertyDesc(),
                        "1",
                        e.description(),
                        e.occurredAt() != null
                                ? e.occurredAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                : null))
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
     * <p>Устройства, которые есть в БД, но отсутствуют в runtime, передаются
     * с флагом {@code disconnected=true}. Фронтенд отображает их как "Отключено".
     *
     * @param instanceId идентификатор аппарата
     * @return сообщение {@code DEVICES_STATUS}, или {@code null} если аппарат неизвестен
     */
    public @Nullable DevicesStatusMessageDTO buildDevicesStatus(String instanceId) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) return null;

        Map<String, String> scadaRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.scadaDeviceName()));

        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        DeviceComposition runtimeComposition = deviceCompositionService.getRuntimeComposition(instanceId);
        Set<String> runtimeDevices = runtimeComposition != null ? runtimeComposition.allDevices() : Set.of();

        List<DevicesStatusMessageDTO.PrinterStatus> printers = buildPrinterStatuses(
                instanceId, composition.printers(), scadaRaw, runtimeDevices);

        List<DevicesStatusMessageDTO.CameraStatus> aggregationCams = buildAggregationCamStatuses(
                instanceId, composition.aggregationCams(), scadaRaw, runtimeDevices);

        List<DevicesStatusMessageDTO.CameraStatus> aggregationBoxCams = buildAggregationBoxCamStatuses(
                instanceId, composition.aggregationBoxCams(), scadaRaw, runtimeDevices);

        List<DevicesStatusMessageDTO.CameraStatus> checkerCams = buildCheckerCamStatuses(
                instanceId, composition.checkerCams(), scadaRaw, runtimeDevices);

        DevicesStatusMessageDTO.Payload payload = new DevicesStatusMessageDTO.Payload(
                printers, aggregationCams, aggregationBoxCams, checkerCams);

        return DevicesStatusMessageDTO.of(instanceId, nowUtc(), payload);
    }

    /**
     * Извлекает список <b>активных</b> ошибок устройств данного инстанса.
     *
    * <p>Источник — runtime scada-флаги {@code DevXXXSuffix} / {@code LineDevXXXSuffix}.
    * Ошибки сопоставляются с объединенным составом из БД и runtime discovery,
    * поэтому временное расхождение topology не скрывает реальную ошибку.
     *
     * <p>Результат предназначен для записи в {@code UnitErrorStore}; используется
     * {@code buildErrorsStatus} и {@link AlertService} как единый источник правды.
     *
     * @param instanceId идентификатор аппарата
     * @return неизменяемый список активных ошибок (пустой, если ошибок нет)
     */
    public @NonNull List<DeviceError> extractActiveErrors(String instanceId) {
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) return List.of();

        DeviceComposition composition = deviceCompositionService.getComposition(instanceId);
        DeviceComposition runtimeComposition = deviceCompositionService.getRuntimeComposition(instanceId);
        List<String> allowedPrefixes = buildErrorDevicePrefixes(instanceId, composition, runtimeComposition);
        if (allowedPrefixes.isEmpty()) {
            return List.of();
        }

        Map<String, List<DeviceError>> errorsByDevice = new LinkedHashMap<>();
        for (String prefix : allowedPrefixes) {
            errorsByDevice.put(prefix, new ArrayList<>());
        }

        Map<String, String> scadaRaw = firstUnitRawProperties(
                snapshotRepo.get(instanceId, inst.scadaDeviceName()));

        for (Map.Entry<String, String> entry : scadaRaw.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            Optional<RuntimeTagMapper.ErrorTag> parsed = RuntimeTagMapper.parseErrorKey(key);
            if (parsed.isEmpty()) {
                // Диагностика только для активных значений неизвестных device-ключей
                // (нулевые флаги и счётчики не логируем — ТЗ §7.7).
                if (RuntimeTagMapper.isPotentialDeviceKey(key)
                        && !RuntimeTagMapper.hasKnownNonErrorSuffix(key)
                        && RuntimeTagMapper.isActiveFlag(value)) {
                    log.debug("[{}] Unknown runtime device error key: {}={}", instanceId, key, value);
                }
                continue;
            }
            if (!RuntimeTagMapper.isActiveFlag(value)) {
                continue;
            }
            String objectName = parsed.get().objectName();
            List<DeviceError> bucket = errorsByDevice.get(objectName);
            if (bucket == null) {
                log.debug("[{}] Ignoring runtime error with unknown device prefix: {}={}",
                        instanceId, key, value);
                continue;
            }
            log.info("[{}] Active runtime error: {}={} device={}", instanceId, key, value, objectName);
            bucket.add(new DeviceError(objectName, key, descriptionForKey(key)));
        }

        // Дедупликация: голый флаг Error подавляется, если у того же устройства
        // активен любой конкретный флаг (Connection/Fail/Dublicate/DiffEan/Work/Data/Batch) —
        // иначе журнал дублирует одну проблему двумя строками (PRINTSRV_UI_MAP.md §4).
        DeviceScadaRegistry.DeviceLayout layout = deviceScadaRegistry.loadLayout(instanceId);
        Map<String, String> scadaPrefixByCode = scadaPrefixByCode(instanceId, layout);

        List<DeviceError> errors = new ArrayList<>();
        for (Map.Entry<String, List<DeviceError>> bucket : errorsByDevice.entrySet()) {
            List<DeviceError> bucketErrors = bucket.getValue();
            boolean hasSpecific = bucketErrors.stream()
                    .anyMatch(e -> !"Error".equals(errorSuffix(e.propertyDesc())));
            String humanName = resolveHumanDeviceName(layout, scadaPrefixByCode, bucket.getKey());
            for (DeviceError error : bucketErrors) {
                if (hasSpecific && "Error".equals(errorSuffix(error.propertyDesc()))) {
                    continue;
                }
                errors.add(new DeviceError(humanName, error.propertyDesc(), error.description()));
            }
        }
        return List.copyOf(errors);
    }

    /**
     * Человекочитаемое имя устройства для журнала/алерта: per-unit display name
     * (иначе имя каталога), с префиксом группы, если группа не машинная
     * («Поток 2. Камера 43»). Если устройство не найдено в раскладке — сырой префикс.
     */
    private @NonNull String resolveHumanDeviceName(
            DeviceScadaRegistry.DeviceLayout layout,
            Map<String, String> scadaPrefixByCode,
            String rawPrefix
    ) {
        return deviceScadaRegistry.findByScadaPrefix(layout, rawPrefix)
                .map(entry -> {
                    String name = entry.effectiveDisplayName() != null
                            ? entry.effectiveDisplayName()
                            : entry.code();
                    String group = deviceGroupService.resolveGroupLabel(layout, entry, scadaPrefixByCode);
                    return group.equals(layout.unitDisplayName()) ? name : group + ". " + name;
                })
                .orElse(rawPrefix);
    }

    private @NonNull Map<String, String> scadaPrefixByCode(
            String instanceId,
            DeviceScadaRegistry.DeviceLayout layout
    ) {
        Map<String, String> prefixes = new LinkedHashMap<>();
        for (DeviceScadaRegistry.DeviceEntry entry : layout.entries()) {
            String prefix = deviceScadaRegistry.resolveScadaPrefix(instanceId, entry.code());
            if (prefix != null) {
                prefixes.put(entry.code(), prefix);
            }
        }
        return prefixes;
    }

    private static @NonNull String errorSuffix(@NonNull String key) {
        return RuntimeTagMapper.parseErrorKey(key)
                .map(RuntimeTagMapper.ErrorTag::suffix)
                .orElse("");
    }

    private @NonNull List<DevicesStatusMessageDTO.PrinterStatus> buildPrinterStatuses(
            String instanceId,
            List<String> printerNames,
            Map<String, String> scadaRaw,
            Set<String> runtimeDevices
    ) {
        List<DevicesStatusMessageDTO.PrinterStatus> result = new ArrayList<>(printerNames.size());
        for (String printerName : printerNames) {
            boolean disconnected = !runtimeDevices.contains(printerName);

            if (disconnected) {
                result.add(new DevicesStatusMessageDTO.PrinterStatus(printerName, null, null, null, true));
                continue;
            }

            DeviceSnapshot snap = snapshotRepo.get(instanceId, printerName);

            String st = getFirstUnit(snap)
                    .map(u -> u.properties().getSt().orElse(null))
                    .orElse(null);
            String error = getFirstUnit(snap)
                    .map(u -> u.properties().getError().orElse(null))
                    .orElse(null);
            String batch = getFirstUnit(snap)
                    .map(u -> u.properties().getCurItem().orElse(null))
                    .orElse(null);

            // Fallback из scada: LineDev0{NN}ST → ошибка принтера по имени устройства
            // Пример: Printer11 → LineDev011ST, Printer12 → LineDev012ST
            // Префиксы — через единый резолвер (настроенный префикс важнее умолчания).
            List<String> printerPrefixes = deviceScadaRegistry.resolveScadaPrefixes(instanceId, printerName);
            if (st == null && !scadaRaw.isEmpty()) {
                for (String scadaPrefix : printerPrefixes) {
                    st = scadaRaw.get(scadaPrefix + "ST");
                    if (st != null) {
                        break;
                    }
                }
            }
            if (error == null) {
                for (String scadaPrefix : printerPrefixes) {
                    if (RuntimeTagMapper.hasActiveError(scadaRaw, scadaPrefix)) {
                        error = "1";
                        break;
                    }
                }
            }

            result.add(new DevicesStatusMessageDTO.PrinterStatus(printerName, st, error, batch, false));
        }
        return result;
    }

    /**
     * Строит статусы aggregation-камер.
     * scada-ключ — через единый резолвер (настроенный префикс или Dev{41 + i*2}).
     */
    private @NonNull List<DevicesStatusMessageDTO.CameraStatus> buildAggregationCamStatuses(
            String instanceId,
            List<String> camNames,
            Map<String, String> scadaRaw,
            Set<String> runtimeDevices
    ) {
        List<DevicesStatusMessageDTO.CameraStatus> result = new ArrayList<>(camNames.size());
        for (int i = 0; i < camNames.size(); i++) {
            String camName = camNames.get(i);
            if (!runtimeDevices.contains(camName)) {
                result.add(new DevicesStatusMessageDTO.CameraStatus(camName, null, null, null, null, null, true));
                continue;
            }
            Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, camName));
            String devKey = deviceScadaRegistry.resolveScadaPrefix(instanceId, camName);
            result.add(buildSingleCamStatus(camName, camRaw, devKey, scadaRaw));
        }
        return result;
    }

    /**
     * Строит статусы aggregation-box-камер.
     * scada-ключ — через единый резолвер (настроенный префикс или Dev{42 + i*2}).
     */
    private @NonNull List<DevicesStatusMessageDTO.CameraStatus> buildAggregationBoxCamStatuses(
            String instanceId,
            List<String> camNames,
            Map<String, String> scadaRaw,
            Set<String> runtimeDevices
    ) {
        List<DevicesStatusMessageDTO.CameraStatus> result = new ArrayList<>(camNames.size());
        for (int i = 0; i < camNames.size(); i++) {
            String camName = camNames.get(i);
            if (!runtimeDevices.contains(camName)) {
                result.add(new DevicesStatusMessageDTO.CameraStatus(camName, null, null, null, null, null, true));
                continue;
            }
            Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, camName));
            String devKey = deviceScadaRegistry.resolveScadaPrefix(instanceId, camName);
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
            Map<String, String> scadaRaw,
            Set<String> runtimeDevices
    ) {
        List<DevicesStatusMessageDTO.CameraStatus> result = new ArrayList<>(camNames.size());
        for (String camName : camNames) {
            if (!runtimeDevices.contains(camName)) {
                result.add(new DevicesStatusMessageDTO.CameraStatus(camName, null, null, null, null, null, true));
                continue;
            }

            Map<String, String> camRaw = firstUnitRawProperties(snapshotRepo.get(instanceId, camName));
            if (ScadaKeyMapper.isEanChecker(camName)) {
                String devKey = deviceScadaRegistry.resolveScadaPrefix(instanceId, camName);
                if (devKey != null) {
                    result.add(buildSingleCamStatus(camName, camRaw, devKey, scadaRaw));
                } else {
                    // Не удалось распознать номер — читаем только device-поля
                    result.add(buildSingleCamStatusDirect(camName, camRaw));
                    log.warn("[{}] Cannot derive scada key for EAN checker: {}", instanceId, camName);
                }
            } else {
                // Обычный checker (CamChecker, CamBatch, CamPacker, …)
                // Читает поля устройства напрямую; профильные ошибки приходят через scada.
                // Если у устройства есть scada-префикс (настроенный или name-based,
                // например misclassified CamAgregation или Trepko CamChecker→Dev03) —
                // читаем scada-first, как камеру агрегации.
                String devKey = deviceScadaRegistry.resolveScadaPrefix(instanceId, camName);
                if (devKey != null) {
                    result.add(buildSingleCamStatus(camName, camRaw, devKey, scadaRaw));
                } else {
                    result.add(buildSingleCamStatusDirect(camName, camRaw));
                }
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
    /**
     * Возвращает описание ошибки по ключу scada: {@code Dev041Fail} → {@code "Нет кодов маркировки"}.
     */
    private static @NonNull String descriptionForKey(String key) {
        return RuntimeTagMapper.parseErrorKey(key)
                .map(tag -> ERROR_DESCRIPTIONS.getOrDefault(tag.suffix(), tag.suffix()))
                .orElse(key);
    }

    private @NonNull List<String> buildErrorDevicePrefixes(
            String instanceId,
            DeviceComposition composition,
            @Nullable DeviceComposition runtimeComposition
    ) {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();

        addErrorPrefixes(instanceId, prefixes, composition);
        if (runtimeComposition != null) {
            addErrorPrefixes(instanceId, prefixes, runtimeComposition);
        }
        return List.copyOf(prefixes);
    }

    /**
     * Добавляет scada-префиксы устройств состава через единый резолвер
     * (настроенный unit_devices.scada_prefix важнее индексных правил).
     * Устройства без scada-префикса добавляются по собственному коду.
     */
    private void addErrorPrefixes(String instanceId, Set<String> prefixes, DeviceComposition composition) {
        List<String> devices = new ArrayList<>();
        devices.addAll(composition.printers());
        devices.addAll(composition.aggregationCams());
        devices.addAll(composition.aggregationBoxCams());
        devices.addAll(composition.checkerCams());
        for (String device : devices) {
            List<String> devicePrefixes = deviceScadaRegistry.resolveScadaPrefixes(instanceId, device);
            if (devicePrefixes.isEmpty()) {
                prefixes.add(device);
            } else {
                prefixes.addAll(devicePrefixes);
            }
        }
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
