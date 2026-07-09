package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceCatalogEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceCatalogJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceTypeJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ручной CRUD-контроллер для управления справочником устройств (device_catalog).
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/device-catalog")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeviceCatalogController {

    private final DeviceCatalogJpaRepository catalogRepository;
    private final DeviceTypeJpaRepository deviceTypeRepository;

    public AdminDeviceCatalogController(DeviceCatalogJpaRepository catalogRepository,
                                        DeviceTypeJpaRepository deviceTypeRepository) {
        this.catalogRepository = catalogRepository;
        this.deviceTypeRepository = deviceTypeRepository;
    }

    @GetMapping
    public ResponseEntity<Page<DeviceCatalogEntity>> list(Pageable pageable) {
        Page<DeviceCatalogEntity> page = catalogRepository.findAll(pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceCatalogEntity> getOne(@PathVariable @NonNull Long id) {
        DeviceCatalogEntity catalog = catalogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено"));
        return ResponseEntity.ok(catalog);
    }

    @PostMapping
    public ResponseEntity<DeviceCatalogEntity> create(@Valid @RequestBody CatalogRequest request) {
        DeviceTypeEntity type = null;
        if (request.typeId() != null) {
            type = deviceTypeRepository.findById(request.typeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тип устройства не найден"));
        }

        // Валидация: нельзя активировать устройство без типа
        if (request.active() && type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Нельзя активировать устройство без указания типа");
        }

        DeviceCatalogEntity catalog = new DeviceCatalogEntity();
        catalog.setType(type);
        catalog.setCode(request.code());
        catalog.setDisplayName(request.displayName());
        catalog.setActive(request.active());

        DeviceCatalogEntity saved = catalogRepository.save(catalog);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceCatalogEntity> update(@PathVariable @NonNull Long id,
                                                      @Valid @RequestBody CatalogRequest request) {
        DeviceCatalogEntity catalog = catalogRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено"));

        DeviceTypeEntity type = null;
        if (request.typeId() != null) {
            type = deviceTypeRepository.findById(request.typeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тип устройства не найден"));
        }

        // Валидация: нельзя активировать устройство без типа
        if (request.active() && type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Нельзя активировать устройство без указания типа");
        }

        catalog.setType(type);
        catalog.setCode(request.code());
        catalog.setDisplayName(request.displayName());
        catalog.setActive(request.active());

        DeviceCatalogEntity saved = catalogRepository.save(catalog);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!catalogRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено");
        }
        catalogRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record CatalogRequest(
            Long typeId,
            @NotBlank String code,
            @NotBlank String displayName,
            boolean active
    ) {
    }
}
