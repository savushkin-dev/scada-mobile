package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
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
 * читает актуальный срез из {@link UnitErrorStore} и возвращает
 * карту всех юнитов, у которых зафиксирована проблема.
 *
 * <h3>Правила определения severity</h3>
 * Использует {@link UnitErrorStore} как единственный источник правды:
 * <ul>
 *   <li><b>Critical</b> — {@code UnitErrorStore} содержит хотя бы одну активную ошибку
 *       для данного аппарата. Текст ошибок берётся из записей store.</li>
 *   <li><b>Нет алёрта</b> — store пуст для данного аппарата (ошибок нет).</li>
 * </ul>
 */
@Service
public class AlertService {

    private static final String SEVERITY_CRITICAL = "Critical";

    private final PrintSrvProperties config;
    private final UnitErrorStore unitErrorStore;

    /**
     * workshopId → инстансы цеха (однократно строится при старте)
     */
    private final Map<String, List<PrintSrvProperties.InstanceProperties>> instancesByWorkshop;
    private final Map<String, PrintSrvProperties.InstanceProperties> instancesById;
    private final Map<String, PrintSrvProperties.WorkshopProperties> workshopsById;

    public AlertService(PrintSrvProperties config,
                        UnitErrorStore unitErrorStore) {
        this.config = config;
        this.unitErrorStore = unitErrorStore;
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

    /**
     * Вычисляет алёрт для одного аппарата на основе данных из {@link UnitErrorStore}.
     *
     * <p>Critical — если store содержит хотя бы одну активную ошибку.
     * Ошибки уже были извлечены из устройства {@code scada} и записаны в store
     * перед вызовом этого метода (через {@code UnitDetailService.extractActiveErrors}).
     */
    private Optional<AlertMessageDTO> computeAlertForUnit(
            PrintSrvProperties.WorkshopProperties workshop,
            PrintSrvProperties.@NonNull InstanceProperties inst,
            String timestamp
    ) {
        List<DeviceError> deviceErrors = unitErrorStore.getErrors(inst.getId());
        if (deviceErrors.isEmpty()) {
            return Optional.empty();
        }

        List<AlertErrorDTO> alertErrors = deviceErrors.stream()
                .map(e -> new AlertErrorDTO(e.objectName(), 0, e.description()))
                .toList();

        return Optional.of(AlertMessageDTO.active(
                workshop.getId(),
                inst.getId(),
                inst.getDisplayName(),
                SEVERITY_CRITICAL,
                alertErrors,
                timestamp
        ));
    }
}
