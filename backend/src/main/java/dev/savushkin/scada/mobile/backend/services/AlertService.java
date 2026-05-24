package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.AlertErrorDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AlertMessageDTO;
import dev.savushkin.scada.mobile.backend.application.ports.PrintSrvTopologyRepository;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceError;
import dev.savushkin.scada.mobile.backend.domain.model.PrintSrvInstance;
import dev.savushkin.scada.mobile.backend.domain.model.Workshop;
import dev.savushkin.scada.mobile.backend.infrastructure.store.UnitErrorStore;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    private final PrintSrvTopologyRepository topologyRepo;
    private final UnitErrorStore unitErrorStore;

    public AlertService(PrintSrvTopologyRepository topologyRepo,
                        UnitErrorStore unitErrorStore) {
        this.topologyRepo = topologyRepo;
        this.unitErrorStore = unitErrorStore;
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

        Map<Long, Workshop> workshopsById = new LinkedHashMap<>();
        for (Workshop ws : topologyRepo.findAllActiveWorkshops()) {
            workshopsById.put(ws.id(), ws);
        }

        for (PrintSrvInstance inst : topologyRepo.findAllActiveInstances()) {
            Workshop workshop = workshopsById.get(inst.workshopId());
            if (workshop == null) {
                continue;
            }
            computeAlertForUnit(workshop, inst, timestamp)
                    .ifPresent(alert -> result.put(inst.instanceId(), alert));
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
        PrintSrvInstance inst = topologyRepo.findByInstanceId(instanceId).orElse(null);
        if (inst == null) {
            return Optional.empty();
        }

        Workshop workshop = topologyRepo.findAllActiveWorkshops().stream()
                .filter(ws -> ws.id() == inst.workshopId())
                .findFirst()
                .orElse(null);
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
            Workshop workshop,
            @NonNull PrintSrvInstance inst,
            String timestamp
    ) {
        List<DeviceError> deviceErrors = unitErrorStore.getErrors(inst.instanceId());
        if (deviceErrors.isEmpty()) {
            return Optional.empty();
        }

        List<AlertErrorDTO> alertErrors = deviceErrors.stream()
                .map(e -> new AlertErrorDTO(e.objectName(), 0, e.description()))
                .toList();

        return Optional.of(AlertMessageDTO.active(
                workshop.id(),
                inst.instanceId(),
                inst.displayName(),
                SEVERITY_CRITICAL,
                alertErrors,
                timestamp
        ));
    }
}
