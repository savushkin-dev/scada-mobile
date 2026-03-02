package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.api.dto.UnitsDTO;
import dev.savushkin.scada.mobile.backend.api.dto.WorkshopsDTO;
import dev.savushkin.scada.mobile.backend.application.ports.InstanceSnapshotRepository;
import dev.savushkin.scada.mobile.backend.config.PrintSrvProperties;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceSnapshot;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для формирования данных REST API цехов и аппаратов.
 * <p>
 * Объединяет статическую конфигурацию из YAML (список цехов и аппаратов)
 * с live-данными из {@link InstanceSnapshotRepository} (текущее состояние).
 */
@Service
public class WorkshopService {

    private static final Logger log = LoggerFactory.getLogger(WorkshopService.class);

    private static final String LINE_DEVICE = "Line";

    private final PrintSrvProperties config;
    private final InstanceSnapshotRepository snapshotRepo;

    /** Быстрый lookup: workshopId → список инстансов. */
    private final Map<String, List<PrintSrvProperties.InstanceProperties>> instancesByWorkshop;

    public WorkshopService(PrintSrvProperties config, InstanceSnapshotRepository snapshotRepo) {
        this.config = config;
        this.snapshotRepo = snapshotRepo;
        this.instancesByWorkshop = config.getInstances().stream()
                .collect(Collectors.groupingBy(
                        PrintSrvProperties.InstanceProperties::getWorkshopId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        log.info("WorkshopService initialized: {} workshops, {} instances",
                config.getWorkshops().size(), config.getInstances().size());
    }

    /**
     * Возвращает список всех цехов с актуальной статистикой problemUnits.
     */
    public List<WorkshopsDTO> getWorkshops() {
        List<WorkshopsDTO> result = new ArrayList<>();
        for (PrintSrvProperties.WorkshopProperties ws : config.getWorkshops()) {
            List<PrintSrvProperties.InstanceProperties> instances =
                    instancesByWorkshop.getOrDefault(ws.getId(), Collections.emptyList());
            int problemUnits = countProblemUnits(instances);
            result.add(new WorkshopsDTO(
                    ws.getId(),
                    ws.getDisplayName(),
                    instances.size(),
                    problemUnits
            ));
        }
        return result;
    }

    /**
     * Возвращает список аппаратов цеха с актуальным событием и таймером.
     *
     * @param workshopId идентификатор цеха
     * @return список аппаратов или пустой список, если цех не найден
     */
    public List<UnitsDTO> getUnits(String workshopId) {
        List<PrintSrvProperties.InstanceProperties> instances =
                instancesByWorkshop.getOrDefault(workshopId, Collections.emptyList());

        List<UnitsDTO> result = new ArrayList<>();
        for (PrintSrvProperties.InstanceProperties inst : instances) {
            String event = deriveEvent(inst.getId());
            String timer = deriveTimer(inst.getId());
            result.add(new UnitsDTO(
                    inst.getId(),
                    inst.getWorkshopId(),
                    inst.getDisplayName(),
                    event,
                    timer
            ));
        }
        return result;
    }

    /**
     * Проверяет, существует ли цех с заданным id.
     */
    public boolean workshopExists(String workshopId) {
        return config.getWorkshops().stream()
                .anyMatch(ws -> ws.getId().equals(workshopId));
    }

    // ─── Внутренние методы формирования live-данных ───────────────────────────

    private int countProblemUnits(List<PrintSrvProperties.InstanceProperties> instances) {
        int count = 0;
        for (PrintSrvProperties.InstanceProperties inst : instances) {
            if (hasActiveError(inst.getId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Определяет, есть ли активная ошибка на инстансе.
     * Проверяет флаг Error на устройстве Line.
     */
    private boolean hasActiveError(String instanceId) {
        DeviceSnapshot lineSnapshot = snapshotRepo.get(instanceId, LINE_DEVICE);
        if (lineSnapshot == null) {
            return false;
        }
        for (UnitSnapshot unit : lineSnapshot.units().values()) {
            Optional<String> error = unit.properties().getError();
            if (error.isPresent() && !"0".equals(error.get()) && !error.get().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Формирует текстовое описание текущего события для аппарата.
     */
    private String deriveEvent(String instanceId) {
        DeviceSnapshot lineSnapshot = snapshotRepo.get(instanceId, LINE_DEVICE);
        if (lineSnapshot == null) {
            return "Нет данных";
        }
        for (UnitSnapshot unit : lineSnapshot.units().values()) {
            Optional<String> st = unit.properties().getSt();
            Optional<String> error = unit.properties().getError();
            Optional<String> errorMsg = unit.properties().getErrorMessage();

            boolean hasError = error.isPresent() && !"0".equals(error.get()) && !error.get().isEmpty();
            if (hasError) {
                return errorMsg.filter(m -> !m.isEmpty()).orElse("Ошибка");
            }

            boolean isRunning = st.isPresent() && "1".equals(st.get());
            return isRunning ? "В работе" : "Остановлена";
        }
        return "Нет данных";
    }

    /**
     * Формирует таймер текущего состояния.
     * <p>
     * Для полноценной реализации требуется отслеживание времени смены состояний
     * (в будущем). Сейчас возвращает {@code "00:00:00"}.
     */
    private String deriveTimer(String instanceId) {
        // TODO: отслеживание времени смены состояний для расчёта реального таймера
        return "00:00:00";
    }
}
