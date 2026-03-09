package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис определения активных алёртов по текущему снапшоту PrintSrv.
 * <p>
 * Является <b>stateless</b>-компонентом — каждый вызов {@link #computeCurrentAlerts()}
 * читает актуальный срез из {@code InstanceSnapshotRepository} и возвращает
 * карту всех юнитов, у которых зафиксирована проблема (ошибка или предупреждение).
 * <p>
 * Сравнение с предыдущим состоянием и определение дельты выполняется
 * в {@code ActiveAlertStore}, которое использует результат этого сервиса.
 *
 * <h3>Правила определения severity</h3>
 * Проверяется устройство {@code Line} для каждого инстанса:
 * <ul>
 *   <li><b>Critical</b> — {@code UnitProperties.getError()} непуст и не {@code "0"}.
 *       Текст ошибки берётся из {@code getErrorMessage()}, при отсутствии — {@code "Ошибка"}.</li>
 *   <li><b>Warning</b> — ошибки нет, но линия остановлена ({@code ST} ≠ {@code "1"}).
 *       Линия стоит без явной ошибки: ждёт материалов, плановая остановка и т.п.</li>
 *   <li><b>Нет алёрта</b> — ошибок нет и линия в работе ({@code ST} = {@code "1"}).</li>
 * </ul>
 * Оба уровня учитываются в счётчике «Проблемных» на дашборде цехов.
 */
@Service
public class AlertService {

    private static final String SEVERITY_CRITICAL = "Critical";
    private static final String SEVERITY_WARNING = "Warning";

    private final PrintSrvProperties config;
    private final InstanceSnapshotRepository snapshotRepo;

    /**
     * workshopId → инстансы цеха (однократно строится при старте)
     */
    private final Map<String, List<PrintSrvProperties.InstanceProperties>> instancesByWorkshop;
    private final Map<String, PrintSrvProperties.InstanceProperties> instancesById;
    private final Map<String, PrintSrvProperties.WorkshopProperties> workshopsById;

    public AlertService(PrintSrvProperties config, InstanceSnapshotRepository snapshotRepo) {
        this.config = config;
        this.snapshotRepo = snapshotRepo;
        this.instancesByWorkshop = config.getInstances().stream()
                .collect(Collectors.groupingBy(
                        PrintSrvProperties.InstanceProperties::getWorkshopId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        this.instancesById = config.getInstances().stream()
                .collect(Collectors.toMap(
                        PrintSrvProperties.InstanceProperties::getId,
                        inst -> inst,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        this.workshopsById = config.getWorkshops().stream()
                .collect(Collectors.toMap(
                        PrintSrvProperties.WorkshopProperties::getId,
                        workshop -> workshop,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    /**
     * Вычисляет карту всех текущих активных алёртов.
     * <p>
     * Ключ — {@code unitId} (идентификатор инстанса/аппарата).
     * Значение — {@link AlertMessageDTO} с {@code active = true}.
     * <p>
     * Инстансы без снапшота или без активных ошибок в результат не попадают.
     *
     * @return неизменяемая карта {@code unitId → AlertMessageDTO}
     */
    public Map<String, AlertMessageDTO> computeCurrentAlerts() {
        Map<String, AlertMessageDTO> result = new LinkedHashMap<>();
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        for (PrintSrvProperties.WorkshopProperties workshop : config.getWorkshops()) {
            List<PrintSrvProperties.InstanceProperties> instances =
                    instancesByWorkshop.getOrDefault(workshop.getId(), Collections.emptyList());

            for (PrintSrvProperties.InstanceProperties inst : instances) {
                computeAlertForUnit(workshop, inst, timestamp)
                        .ifPresent(alert -> result.put(inst.getId(), alert));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Вычисляет текущий активный алёрт только для одного инстанса.
     *
     * @param instanceId идентификатор аппарата
     * @return активный алёрт или пусто, если проблем нет либо инстанс неизвестен
     */
    public Optional<AlertMessageDTO> computeAlertForInstance(String instanceId) {
        PrintSrvProperties.InstanceProperties inst = instancesById.get(instanceId);
        if (inst == null) {
            return Optional.empty();
        }

        PrintSrvProperties.WorkshopProperties workshop = workshopsById.get(inst.getWorkshopId());
        if (workshop == null) {
            return Optional.empty();
        }

        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return computeAlertForUnit(workshop, inst, timestamp);
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private Optional<AlertMessageDTO> computeAlertForUnit(
            PrintSrvProperties.WorkshopProperties workshop,
            PrintSrvProperties.@NonNull InstanceProperties inst,
            String timestamp
    ) {
        String lineDevice = inst.getDevices().getLine();
        DeviceSnapshot lineSnapshot = snapshotRepo.get(inst.getId(), lineDevice);
        if (lineSnapshot == null) {
            return Optional.empty();
        }

        // Critical имеет приоритет: если есть ошибка — Warning не проверяем.
        List<AlertErrorDTO> errors = collectErrors(lineSnapshot);
        if (!errors.isEmpty()) {
            return Optional.of(AlertMessageDTO.active(
                    workshop.getId(),
                    inst.getId(),
                    inst.getDisplayName(),
                    SEVERITY_CRITICAL,
                    errors,
                    timestamp
            ));
        }

        // Warning: линия остановлена, но без явной ошибки.
        if (isLineStopped(lineSnapshot)) {
            return Optional.of(AlertMessageDTO.active(
                    workshop.getId(),
                    inst.getId(),
                    inst.getDisplayName(),
                    SEVERITY_WARNING,
                    List.of(new AlertErrorDTO(lineDevice, 0, "Линия остановлена")),
                    timestamp
            ));
        }

        return Optional.empty();
    }

    /**
     * Собирает список ошибок из всех юнитов снапшота устройства {@code Line}.
     * Возвращает непустой список только при {@code SEVERITY_CRITICAL}.
     */
    private @NonNull List<AlertErrorDTO> collectErrors(@NonNull DeviceSnapshot snapshot) {
        List<AlertErrorDTO> errors = new ArrayList<>();
        for (UnitSnapshot unit : snapshot.units().values()) {
            Optional<String> error = unit.properties().getError();
            if (error.isPresent() && !error.get().isEmpty() && !"0".equals(error.get())) {
                String message = unit.properties().getErrorMessage()
                        .filter(m -> !m.isEmpty())
                        .orElse("Ошибка");
                errors.add(new AlertErrorDTO(snapshot.deviceName(), 0, message));
            }
        }
        return errors;
    }

    /**
     * Возвращает {@code true}, если линия остановлена ({@code ST} ≠ {@code "1"})
     * хотя бы в одном из юнитов снапшота.
     * Используется для формирования {@code SEVERITY_WARNING}.
     */
    private boolean isLineStopped(@NonNull DeviceSnapshot snapshot) {
        for (UnitSnapshot unit : snapshot.units().values()) {
            Optional<String> st = unit.properties().getSt();
            // ST отсутствует или не "1" — линия не в работе
            if (st.isEmpty() || !"1".equals(st.get())) {
                return true;
            }
        }
        return false;
    }
}
