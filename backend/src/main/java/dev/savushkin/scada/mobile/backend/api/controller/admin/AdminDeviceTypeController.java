package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.domain.model.ChangeAction;
import dev.savushkin.scada.mobile.backend.domain.model.DeviceTypeChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.DeviceTypeJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    private final ApplicationEventPublisher eventPublisher;

    public AdminDeviceTypeController(DeviceTypeJpaRepository deviceTypeRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.deviceTypeRepository = deviceTypeRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DeviceTypeEntity> create(@Valid @RequestBody DeviceTypeRequest request) {
        if (deviceTypeRepository.findByCode(request.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Тип устройства с таким кодом уже существует");
        }
        if (deviceTypeRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Тип устройства с таким названием уже существует");
        }

        DeviceTypeEntity type = new DeviceTypeEntity();
        type.setCode(request.code());
        type.setName(request.name());

        DeviceTypeEntity saved = deviceTypeRepository.save(type);
        eventPublisher.publishEvent(new DeviceTypeChangedEvent(saved.getId(), ChangeAction.CREATE));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<DeviceTypeEntity> update(@PathVariable @NonNull Long id,
                                                   @Valid @RequestBody DeviceTypeRequest request) {
        DeviceTypeEntity type = deviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Тип устройства не найден"));

        if (!type.getCode().equals(request.code()) && deviceTypeRepository.findByCode(request.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Тип устройства с таким кодом уже существует");
        }
        if (!type.getName().equals(request.name()) && deviceTypeRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Тип устройства с таким названием уже существует");
        }

        type.setCode(request.code());
        type.setName(request.name());

        DeviceTypeEntity saved = deviceTypeRepository.save(type);
        eventPublisher.publishEvent(new DeviceTypeChangedEvent(saved.getId(), ChangeAction.UPDATE));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!deviceTypeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Тип устройства не найден");
        }
        deviceTypeRepository.deleteById(id);
        eventPublisher.publishEvent(new DeviceTypeChangedEvent(id, ChangeAction.DELETE));
        return ResponseEntity.noContent().build();
    }

    public record DeviceTypeRequest(@NotBlank String code, @NotBlank String name) {
    }
}
