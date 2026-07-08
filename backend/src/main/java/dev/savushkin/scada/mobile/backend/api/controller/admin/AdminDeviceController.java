package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceCatalogJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ручной CRUD-контроллер для управления связями устройств с автоматами (unit_devices).
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/devices")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeviceController {

    private final DeviceJpaRepository deviceRepository;
    private final UnitJpaRepository unitRepository;
    private final DeviceCatalogJpaRepository catalogRepository;

    public AdminDeviceController(DeviceJpaRepository deviceRepository,
                                 UnitJpaRepository unitRepository,
                                 DeviceCatalogJpaRepository catalogRepository) {
        this.deviceRepository = deviceRepository;
        this.unitRepository = unitRepository;
        this.catalogRepository = catalogRepository;
    }

    @PostMapping
    public ResponseEntity<DeviceEntity> create(@Valid @RequestBody DeviceRequest request) {
        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));
        DeviceCatalogEntity catalog = catalogRepository.findById(request.catalogId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Устройство не найдено в справочнике"));

        DeviceEntity device = new DeviceEntity();
        device.setUnit(unit);
        device.setCatalog(catalog);

        DeviceEntity saved = deviceRepository.save(device);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceEntity> update(@PathVariable @NonNull Long id,
                                               @Valid @RequestBody DeviceRequest request) {
        DeviceEntity device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Связь не найдена"));

        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));
        DeviceCatalogEntity catalog = catalogRepository.findById(request.catalogId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Устройство не найдено в справочнике"));

        device.setUnit(unit);
        device.setCatalog(catalog);

        DeviceEntity saved = deviceRepository.save(device);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Связь не найдена");
        }
        deviceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record DeviceRequest(
            @NotNull Long unitId,
            @NotNull Long catalogId
    ) {
    }
}
