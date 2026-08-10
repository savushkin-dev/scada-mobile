package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.api.dto.ErrorResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.ReferenceDTO;
import dev.savushkin.scada.mobile.backend.domain.model.ChangeAction;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceChangedEvent;
import dev.savushkin.scada.mobile.backend.domain.model.UnitChangedEvent;
import dev.savushkin.scada.mobile.backend.domain.model.UserNotificationSettingsChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter.PrintSrvTopologyJpaAdapter;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.WorkshopEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceCatalogJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserNotificationSettingsJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.WorkshopJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ручной CRUD-контроллер для управления unit (аппаратами/линиями).
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/units")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUnitController {

    private final UnitJpaRepository unitRepository;
    private final WorkshopJpaRepository workshopRepository;
    private final DeviceJpaRepository deviceRepository;
    private final DeviceCatalogJpaRepository catalogRepository;
    private final UserAssignmentJpaRepository assignmentRepository;
    private final UserNotificationSettingsJpaRepository notificationSettingsRepository;
    private final PrintSrvTopologyJpaAdapter topologyJpaAdapter;
    private final ApplicationEventPublisher eventPublisher;

    public AdminUnitController(UnitJpaRepository unitRepository,
                               WorkshopJpaRepository workshopRepository,
                               DeviceJpaRepository deviceRepository,
                               DeviceCatalogJpaRepository catalogRepository,
                               UserAssignmentJpaRepository assignmentRepository,
                               UserNotificationSettingsJpaRepository notificationSettingsRepository,
                               PrintSrvTopologyJpaAdapter topologyJpaAdapter,
                               ApplicationEventPublisher eventPublisher) {
        this.unitRepository = unitRepository;
        this.workshopRepository = workshopRepository;
        this.deviceRepository = deviceRepository;
        this.catalogRepository = catalogRepository;
        this.assignmentRepository = assignmentRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.topologyJpaAdapter = topologyJpaAdapter;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UnitEntity> create(@Valid @RequestBody UnitRequest request) {
        WorkshopEntity workshop = workshopRepository.findById(request.workshopId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цех не найден"));

        if (unitRepository.findByNameAndPrintsrvInstanceId(request.name(), request.printsrvInstanceId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Аппарат с таким названием и PrintSrv ID уже существует");
        }

        UnitEntity unit = new UnitEntity();
        unit.setName(request.name());
        unit.setWorkshop(workshop);
        unit.setPrintsrvInstanceId(request.printsrvInstanceId());
        unit.setPrintsrvHost(request.printsrvHost());
        unit.setPrintsrvPort(request.printsrvPort());
        unit.setActive(request.active());

        UnitEntity saved = unitRepository.save(unit);
        syncDevices(saved, request.catalogIds());
        topologyJpaAdapter.invalidateETag();
        eventPublisher.publishEvent(new UnitChangedEvent(saved.getId(), null, null, ChangeAction.CREATE));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<UnitEntity> update(@PathVariable @NonNull Long id,
                                             @Valid @RequestBody UnitRequest request) {
        UnitEntity unit = unitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Аппарат не найден"));

        WorkshopEntity workshop = workshopRepository.findById(request.workshopId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цех не найден"));

        boolean sameName = unit.getName().equals(request.name());
        boolean samePrintsrv = (unit.getPrintsrvInstanceId() == null && request.printsrvInstanceId() == null)
                || (unit.getPrintsrvInstanceId() != null && unit.getPrintsrvInstanceId().equals(request.printsrvInstanceId()));
        if (!(sameName && samePrintsrv)) {
            unitRepository.findByNameAndPrintsrvInstanceId(request.name(), request.printsrvInstanceId())
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Аппарат с таким названием и PrintSrv ID уже существует");
                        }
                    });
        }

        unit.setName(request.name());
        unit.setWorkshop(workshop);
        unit.setPrintsrvInstanceId(request.printsrvInstanceId());
        unit.setPrintsrvHost(request.printsrvHost());
        unit.setPrintsrvPort(request.printsrvPort());
        unit.setActive(request.active());

        UnitEntity saved = unitRepository.save(unit);
        syncDevices(saved, request.catalogIds());
        topologyJpaAdapter.invalidateETag();
        eventPublisher.publishEvent(new UnitChangedEvent(saved.getId(), null, null, ChangeAction.UPDATE));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable @NonNull Long id, HttpServletRequest request) {
        UnitEntity unit = unitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Аппарат не найден"));
        // Автомат, закреплённый хотя бы за одним сотрудником, удалять нельзя:
        // возвращаем 409 со списком сотрудников, чтобы их отвязали перед удалением.
        List<UserAssignmentEntity> activeAssignments =
                assignmentRepository.findTop10ByUnit_IdAndActiveTrueOrderByIdAsc(id);
        if (!activeAssignments.isEmpty()) {
            List<ReferenceDTO> references = activeAssignments.stream()
                    .map(a -> new ReferenceDTO("users", "Сотрудники", a.getUser().getId(),
                            a.getUser().getFullName()))
                    .toList();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponseDTO(
                    HttpStatus.CONFLICT.value(),
                    "Невозможно удалить запись, так как она используется другими объектами системы",
                    request.getRequestURI(), null, references));
        }
        String printsrvInstanceId = unit.getPrintsrvInstanceId();
        Long workshopId = unit.getWorkshopId();
        // Составная сущность «Автомат»: удаляем все оперативные записи,
        // ссылающиеся на автомат, до удаления самой записи в units.
        assignmentRepository.deleteByUnit_Id(id);
        // Настройки уведомлений удаляются массово — читаем затронутые строки заранее,
        // чтобы каждый пользователь получил персональное WS-событие об удалении.
        List<UserNotificationSettingsEntity> removedSettings = notificationSettingsRepository.findByUnit_Id(id);
        notificationSettingsRepository.deleteByUnit_Id(id);
        for (UserNotificationSettingsEntity settings : removedSettings) {
            eventPublisher.publishEvent(new UserNotificationSettingsChangedEvent(
                    settings.getId(), settings.getUserId(), ChangeAction.DELETE));
        }
        // Связи с устройствами тоже уходят с событием — клиенты инвалидируют
        // кэш топологии устройств этого аппарата.
        for (DeviceEntity device : deviceRepository.findByUnit_Id(id)) {
            eventPublisher.publishEvent(new DeviceChangedEvent(
                    device.getId(), id, printsrvInstanceId, ChangeAction.DELETE));
        }
        deviceRepository.deleteByUnit_Id(id);
        unitRepository.deleteById(id);
        topologyJpaAdapter.invalidateETag();
        eventPublisher.publishEvent(new UnitChangedEvent(id, printsrvInstanceId, workshopId, ChangeAction.DELETE));
        return ResponseEntity.noContent().build();
    }

    /**
     * Синхронизирует связи автомата со справочником устройств.
     *
     * @param unit        автомат
     * @param catalogIds  желаемый список ID из device_catalog (null — не менять)
     */
    @Transactional
    private void syncDevices(UnitEntity unit, List<Long> catalogIds) {
        if (catalogIds == null) {
            return;
        }

        Set<Long> newCatalogIds = new HashSet<>(catalogIds);
        List<DeviceEntity> currentDevices = deviceRepository.findByUnit_Id(unit.getId());
        Set<Long> currentCatalogIds = new HashSet<>();
        for (DeviceEntity device : currentDevices) {
            currentCatalogIds.add(device.getCatalog().getId());
        }

        // Удалить лишние связи
        for (DeviceEntity device : currentDevices) {
            if (!newCatalogIds.contains(device.getCatalog().getId())) {
                deviceRepository.delete(device);
                eventPublisher.publishEvent(new DeviceChangedEvent(
                        device.getId(), unit.getId(), unit.getPrintsrvInstanceId(), ChangeAction.DELETE));
            }
        }

        // Добавить новые связи
        for (Long catalogId : newCatalogIds) {
            if (currentCatalogIds.contains(catalogId)) {
                continue;
            }
            DeviceCatalogEntity catalog = catalogRepository.findById(catalogId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Устройство не найдено в справочнике: " + catalogId));
            DeviceEntity device = new DeviceEntity();
            device.setUnit(unit);
            device.setCatalog(catalog);
            DeviceEntity saved = deviceRepository.save(device);
            eventPublisher.publishEvent(new DeviceChangedEvent(saved.getId(), null, null, ChangeAction.CREATE));
        }
    }

    public record UnitRequest(
            @NotBlank String name,
            @NotNull Long workshopId,
            String printsrvInstanceId,
            String printsrvHost,
            Integer printsrvPort,
            boolean active,
            List<Long> catalogIds
    ) {
    }
}
