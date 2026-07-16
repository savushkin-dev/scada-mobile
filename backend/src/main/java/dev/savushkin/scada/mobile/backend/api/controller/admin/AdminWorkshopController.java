package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.domain.model.ChangeAction;
import dev.savushkin.scada.mobile.backend.domain.model.WorkshopChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.WorkshopEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.WorkshopJpaRepository;
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
 * Ручной CRUD-контроллер для управления цехами.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/workshops")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWorkshopController {

    private final WorkshopJpaRepository workshopRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminWorkshopController(WorkshopJpaRepository workshopRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.workshopRepository = workshopRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<WorkshopEntity> create(@Valid @RequestBody WorkshopRequest request) {
        if (workshopRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Цех с таким названием уже существует");
        }

        WorkshopEntity workshop = new WorkshopEntity();
        workshop.setName(request.name());
        workshop.setActive(request.active());

        WorkshopEntity saved = workshopRepository.save(workshop);
        eventPublisher.publishEvent(new WorkshopChangedEvent(saved.getId(), ChangeAction.CREATE));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<WorkshopEntity> update(@PathVariable @NonNull Long id,
                                                 @Valid @RequestBody WorkshopRequest request) {
        WorkshopEntity workshop = workshopRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Цех не найден"));

        if (!workshop.getName().equals(request.name()) && workshopRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Цех с таким названием уже существует");
        }

        workshop.setName(request.name());
        workshop.setActive(request.active());

        WorkshopEntity saved = workshopRepository.save(workshop);
        eventPublisher.publishEvent(new WorkshopChangedEvent(saved.getId(), ChangeAction.UPDATE));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!workshopRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Цех не найден");
        }
        workshopRepository.deleteById(id);
        eventPublisher.publishEvent(new WorkshopChangedEvent(id, ChangeAction.DELETE));
        return ResponseEntity.noContent().build();
    }

    public record WorkshopRequest(@NotBlank String name, boolean active) {
    }
}
