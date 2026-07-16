package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.domain.model.ChangeAction;
import dev.savushkin.scada.mobile.backend.domain.model.UnitChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter.PrintSrvTopologyJpaAdapter;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.WorkshopEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceCatalogJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.WorkshopJpaRepository;
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
    private final PrintSrvTopologyJpaAdapter topologyJpaAdapter;
    private final ApplicationEventPublisher eventPublisher;

    public AdminUnitController(UnitJpaRepository unitRepository,
                               WorkshopJpaRepository workshopRepository,
                               DeviceJpaRepository deviceRepository,
                               DeviceCatalogJpaRepository catalogRepository,
                               PrintSrvTopologyJpaAdapter topologyJpaAdapter,
                               ApplicationEventPublisher eventPublisher) {
        this.unitRepository = unitRepository;
        this.workshopRepository = workshopRepository;
        this.deviceRepository = deviceRepository;
        this.catalogRepository = catalogRepository;
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
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        UnitEntity unit = unitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Аппарат не найден"));
        String printsrvInstanceId = unit.getPrintsrvInstanceId();
        Long workshopId = unit.getWorkshopId();
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
            deviceRepository.save(device);
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
