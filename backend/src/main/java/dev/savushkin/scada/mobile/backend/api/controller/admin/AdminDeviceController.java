package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceTypeJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ручной CRUD-контроллер для управления устройствами (unit_devices).
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/devices")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeviceController {

    private final DeviceJpaRepository deviceRepository;
    private final UnitJpaRepository unitRepository;
    private final DeviceTypeJpaRepository deviceTypeRepository;

    public AdminDeviceController(DeviceJpaRepository deviceRepository,
                                 UnitJpaRepository unitRepository,
                                 DeviceTypeJpaRepository deviceTypeRepository) {
        this.deviceRepository = deviceRepository;
        this.unitRepository = unitRepository;
        this.deviceTypeRepository = deviceTypeRepository;
    }

    @PostMapping
    public ResponseEntity<DeviceEntity> create(@Valid @RequestBody DeviceRequest request) {
        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));
        DeviceTypeEntity type = deviceTypeRepository.findById(request.typeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тип устройства не найден"));

        DeviceEntity device = new DeviceEntity();
        device.setUnit(unit);
        device.setType(type);
        device.setCode(request.code());
        device.setDisplayName(request.displayName());

        DeviceEntity saved = deviceRepository.save(device);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceEntity> update(@PathVariable @NonNull Long id,
                                               @Valid @RequestBody DeviceRequest request) {
        DeviceEntity device = deviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено"));

        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));
        DeviceTypeEntity type = deviceTypeRepository.findById(request.typeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тип устройства не найден"));

        device.setUnit(unit);
        device.setType(type);
        device.setCode(request.code());
        device.setDisplayName(request.displayName());

        DeviceEntity saved = deviceRepository.save(device);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!deviceRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено");
        }
        deviceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record DeviceRequest(
            @NotNull Long unitId,
            @NotNull Long typeId,
            @NotBlank String code,
            @NotBlank String displayName
    ) {
    }
}
