package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceTypeJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ручной CRUD-контроллер для управления типами устройств.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/device-types")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeviceTypeController {

    private final DeviceTypeJpaRepository deviceTypeRepository;

    public AdminDeviceTypeController(DeviceTypeJpaRepository deviceTypeRepository) {
        this.deviceTypeRepository = deviceTypeRepository;
    }

    @PostMapping
    public ResponseEntity<DeviceTypeEntity> create(@Valid @RequestBody DeviceTypeRequest request) {
        DeviceTypeEntity type = new DeviceTypeEntity();
        type.setCode(request.code());
        type.setName(request.name());

        DeviceTypeEntity saved = deviceTypeRepository.save(type);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceTypeEntity> update(@PathVariable @NonNull Long id,
                                                   @Valid @RequestBody DeviceTypeRequest request) {
        DeviceTypeEntity type = deviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Тип устройства не найден"));

        type.setCode(request.code());
        type.setName(request.name());

        DeviceTypeEntity saved = deviceTypeRepository.save(type);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!deviceTypeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Тип устройства не найден");
        }
        deviceTypeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record DeviceTypeRequest(@NotBlank String code, @NotBlank String name) {
    }
}
