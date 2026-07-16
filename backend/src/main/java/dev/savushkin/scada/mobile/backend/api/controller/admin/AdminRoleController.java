package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.domain.model.ChangeAction;
import dev.savushkin.scada.mobile.backend.domain.model.RoleChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.RoleJpaRepository;
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
 * Ручной CRUD-контроллер для управления ролями.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/roles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {

    private final RoleJpaRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminRoleController(RoleJpaRepository roleRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<RoleEntity> create(@Valid @RequestBody RoleRequest request) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Роль с таким названием уже существует");
        }

        RoleEntity role = new RoleEntity();
        role.setName(request.name());

        RoleEntity saved = roleRepository.save(role);
        eventPublisher.publishEvent(new RoleChangedEvent(saved.getId(), ChangeAction.CREATE));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<RoleEntity> update(@PathVariable @NonNull Long id,
                                             @Valid @RequestBody RoleRequest request) {
        RoleEntity role = roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Роль не найдена"));

        if (!role.getName().equals(request.name()) && roleRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Роль с таким названием уже существует");
        }

        role.setName(request.name());

        RoleEntity saved = roleRepository.save(role);
        eventPublisher.publishEvent(new RoleChangedEvent(saved.getId(), ChangeAction.UPDATE));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!roleRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Роль не найдена");
        }
        roleRepository.deleteById(id);
        eventPublisher.publishEvent(new RoleChangedEvent(id, ChangeAction.DELETE));
        return ResponseEntity.noContent().build();
    }

    public record RoleRequest(@NotBlank String name) {
    }
}
