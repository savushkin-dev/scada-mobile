package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.WorkshopEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.WorkshopJpaRepository;
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
 * Ручной CRUD-контроллер для управления unit (аппаратами/линиями).
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/units")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUnitController {

    private final UnitJpaRepository unitRepository;
    private final WorkshopJpaRepository workshopRepository;

    public AdminUnitController(UnitJpaRepository unitRepository,
                               WorkshopJpaRepository workshopRepository) {
        this.unitRepository = unitRepository;
        this.workshopRepository = workshopRepository;
    }

    @PostMapping
    public ResponseEntity<UnitEntity> create(@Valid @RequestBody UnitRequest request) {
        WorkshopEntity workshop = workshopRepository.findById(request.workshopId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цех не найден"));

        UnitEntity unit = new UnitEntity();
        unit.setName(request.name());
        unit.setWorkshop(workshop);
        unit.setPrintsrvInstanceId(request.printsrvInstanceId());
        unit.setActive(request.active());

        UnitEntity saved = unitRepository.save(unit);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UnitEntity> update(@PathVariable @NonNull Long id,
                                             @Valid @RequestBody UnitRequest request) {
        UnitEntity unit = unitRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Аппарат не найден"));

        WorkshopEntity workshop = workshopRepository.findById(request.workshopId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Цех не найден"));

        unit.setName(request.name());
        unit.setWorkshop(workshop);
        unit.setPrintsrvInstanceId(request.printsrvInstanceId());
        unit.setActive(request.active());

        UnitEntity saved = unitRepository.save(unit);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!unitRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Аппарат не найден");
        }
        unitRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record UnitRequest(
            @NotBlank String name,
            @NotNull Long workshopId,
            String printsrvInstanceId,
            boolean active
    ) {
    }
}
