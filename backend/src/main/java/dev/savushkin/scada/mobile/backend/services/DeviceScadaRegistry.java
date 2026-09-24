package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.domain.model.DeviceComposition;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Единая точка разрешения scada-префиксов устройств аппарата.
 *
 * <p>Приоритет: настроенный {@code unit_devices.scada_prefix}, затем индексные
 * правила {@link ScadaKeyMapper} (позиция устройства в составе аппарата).
 * Также предоставляет per-unit раскладку устройств (имена, группы, порядок,
 * признак счётчиков) для группировки, именования ошибок и выбора счётчиков.
 */
@Component
public class DeviceScadaRegistry {

    private final DeviceJpaRepository deviceRepository;
    private final UnitJpaRepository unitRepository;
    private final DeviceCompositionService compositionService;

    public DeviceScadaRegistry(DeviceJpaRepository deviceRepository,
                               UnitJpaRepository unitRepository,
                               DeviceCompositionService compositionService) {
        this.deviceRepository = deviceRepository;
        this.unitRepository = unitRepository;
        this.compositionService = compositionService;
    }

    /**
     * Per-unit запись устройства из БД.
     *
     * @param code           runtime-код устройства
     * @param catalogName    имя из справочника (device_catalog.name)
     * @param displayName    per-unit переопределение имени (NULL → catalogName)
     * @param groupLabel     per-unit метка группы (NULL → правила умолчания)
     * @param displayOrder   порядок отображения
     * @param showCounters   показывать счётчики
     * @param scadaPrefix    настроенный scada-префикс (NULL → вычислять)
     * @param typeCode       код типа устройства (NULL, если тип не назначен)
     * @param hidden         скрыто с экрана (не участвует в группах/счётчиках UI)
     */
    public record DeviceEntry(
            @NonNull String code,
            @Nullable String catalogName,
            @Nullable String displayName,
            @Nullable String groupLabel,
            int displayOrder,
            boolean showCounters,
            @Nullable String scadaPrefix,
            @Nullable String typeCode,
            boolean hidden
    ) {
        public @Nullable String effectiveDisplayName() {
            if (displayName != null && !displayName.isBlank()) {
                return displayName;
            }
            return catalogName;
        }
    }

    /**
     * Раскладка устройств аппарата: отображаемое имя аппарата + записи из БД.
     */
    public record DeviceLayout(@NonNull String instanceId, @NonNull String unitDisplayName, @NonNull List<DeviceEntry> entries) {
        public @NonNull Optional<DeviceEntry> findByCode(@NonNull String code) {
            return entries.stream().filter(e -> e.code().equals(code)).findFirst();
        }
    }

    /**
     * Загружает per-unit раскладку устройств из БД.
     * Неактивные записи справочника пропускаются (как в топологии).
     */
    public @NonNull DeviceLayout loadLayout(@NonNull String instanceId) {
        UnitEntity unit = unitRepository.findByPrintsrvInstanceId(instanceId).orElse(null);
        String unitName = unit != null && unit.getName() != null && !unit.getName().isBlank()
                ? unit.getName()
                : instanceId;
        if (unit == null) {
            return new DeviceLayout(instanceId, unitName, List.of());
        }
        List<DeviceEntry> entries = deviceRepository.findByUnit_PrintsrvInstanceId(instanceId).stream()
                .filter(d -> d.getCatalog() != null && d.getCatalog().isActive())
                .map(this::toEntry)
                .toList();
        return new DeviceLayout(instanceId, unitName, entries);
    }

    /**
     * Возвращает основной scada-префикс устройства: настроенный, иначе по правилам умолчания.
     */
    public @Nullable String resolveScadaPrefix(@NonNull String instanceId, @NonNull String deviceCode) {
        List<String> prefixes = resolveScadaPrefixes(instanceId, deviceCode);
        return prefixes.isEmpty() ? null : prefixes.getFirst();
    }

    /**
     * Возвращает все scada-префиксы устройства: настроенный (один), иначе кандидаты
     * по умолчанию (для принтеров — варианты нулевого заполнения).
     * Пустой список означает, что устройство не читается через scada-префикс.
     */
    public @NonNull List<String> resolveScadaPrefixes(@NonNull String instanceId, @NonNull String deviceCode) {
        return resolveScadaPrefixes(instanceId, loadLayout(instanceId), deviceCode);
    }

    private @NonNull List<String> resolveScadaPrefixes(@NonNull String instanceId,
                                                       @NonNull DeviceLayout layout,
                                                       @NonNull String deviceCode) {
        DeviceEntry entry = layout.findByCode(deviceCode).orElse(null);
        if (entry != null && entry.scadaPrefix() != null && !entry.scadaPrefix().isBlank()) {
            return List.of(entry.scadaPrefix().trim());
        }
        if (entry != null) {
            return defaultCandidatesFor(layout, entry);
        }
        return defaultPrefixesFromComposition(runtimeOrDbComposition(instanceId), deviceCode);
    }

    /**
     * Дефолтные кандидаты для устройства из раскладки: сначала по позиции
     * в составе аппарата, затем по типу и индексу внутри типа в раскладке.
     */
    private @NonNull List<String> defaultCandidatesFor(@NonNull DeviceLayout layout, @NonNull DeviceEntry entry) {
        List<String> fromComposition = defaultPrefixesFromComposition(
                runtimeOrDbComposition(layout.instanceId()), entry.code());
        if (!fromComposition.isEmpty()) {
            return fromComposition;
        }
        if (entry.typeCode() != null) {
            String prefix = ScadaKeyMapper.defaultPrefix(
                    entry.typeCode(), entry.code(), indexWithinType(layout, entry));
            return prefix != null ? List.of(prefix) : List.of();
        }
        return List.of();
    }

    /**
     * Находит устройство аппарата по scada-префиксу (настроенному или умолчному).
     */
    public @NonNull Optional<DeviceEntry> findByScadaPrefix(@NonNull String instanceId, @NonNull String prefix) {
        return findByScadaPrefix(loadLayout(instanceId), prefix);
    }

    public @NonNull Optional<DeviceEntry> findByScadaPrefix(@NonNull DeviceLayout layout, @NonNull String prefix) {
        for (DeviceEntry entry : layout.entries()) {
            if (entry.scadaPrefix() != null && entry.scadaPrefix().equals(prefix)) {
                return Optional.of(entry);
            }
        }
        for (DeviceEntry entry : layout.entries()) {
            List<String> candidates = entry.scadaPrefix() != null && !entry.scadaPrefix().isBlank()
                    ? List.of(entry.scadaPrefix().trim())
                    : defaultCandidatesFor(layout, entry);
            if (candidates.contains(prefix)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private @NonNull DeviceEntry toEntry(@NonNull DeviceEntity device) {
        String typeCode = device.getCatalog().getType() != null
                ? device.getCatalog().getType().getCode()
                : null;
        return new DeviceEntry(
                device.getCode(),
                device.getCatalog().getName(),
                device.getDisplayNameOverride(),
                device.getGroupLabel(),
                device.getDisplayOrder(),
                device.isShowCounters(),
                device.getScadaPrefix(),
                typeCode,
                device.isHidden()
        );
    }

    private @NonNull DeviceComposition runtimeOrDbComposition(@NonNull String instanceId) {
        DeviceComposition runtime = compositionService.getRuntimeComposition(instanceId);
        return runtime != null ? runtime : compositionService.getComposition(instanceId);
    }

    /**
     * Дефолтные префиксы: name-based по runtime-коду; индексные правила по
     * позиции в составе — последний резерв для нестандартных имён.
     */
    private static @NonNull List<String> defaultPrefixesFromComposition(
            @NonNull DeviceComposition composition,
            @NonNull String deviceCode
    ) {
        if (deviceCode.startsWith("Printer") && composition.printers().contains(deviceCode)) {
            return ScadaKeyMapper.printerScadaPrefixes(deviceCode);
        }
        String byName = ScadaKeyMapper.defaultPrefixForCode(deviceCode);
        if (byName != null) {
            return List.of(byName);
        }
        int idx = composition.aggregationCams().indexOf(deviceCode);
        if (idx >= 0) {
            return List.of(ScadaKeyMapper.aggregationCamScadaPrefix(idx));
        }
        idx = composition.aggregationBoxCams().indexOf(deviceCode);
        if (idx >= 0) {
            return List.of(ScadaKeyMapper.aggregationBoxCamScadaPrefix(idx));
        }
        return List.of();
    }

    private static int indexWithinType(@NonNull DeviceLayout layout, @NonNull DeviceEntry entry) {
        List<DeviceEntry> sameType = new ArrayList<>();
        for (DeviceEntry e : layout.entries()) {
            if (e.typeCode() != null && e.typeCode().equals(entry.typeCode())) {
                sameType.add(e);
            }
        }
        int idx = sameType.indexOf(entry);
        return Math.max(idx, 0);
    }
}
