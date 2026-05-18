package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Ручной CRUD-контроллер для управления назначениями пользователей на аппараты.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/user-assignments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserAssignmentController {

    private final UserAssignmentJpaRepository assignmentRepository;
    private final UserJpaRepository userRepository;
    private final UnitJpaRepository unitRepository;

    public AdminUserAssignmentController(UserAssignmentJpaRepository assignmentRepository,
                                         UserJpaRepository userRepository,
                                         UnitJpaRepository unitRepository) {
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
    }

    @PostMapping
    public ResponseEntity<UserAssignmentEntity> create(@Valid @RequestBody AssignmentRequest request) {
        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пользователь не найден"));
        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));

        UserAssignmentEntity assignment = new UserAssignmentEntity();
        assignment.setUser(user);
        assignment.setUnit(unit);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setActive(request.active());

        UserAssignmentEntity saved = assignmentRepository.save(assignment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserAssignmentEntity> update(@PathVariable @NonNull Long id,
                                                       @Valid @RequestBody AssignmentRequest request) {
        UserAssignmentEntity assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Назначение не найдено"));

        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пользователь не найден"));
        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));

        assignment.setUser(user);
        assignment.setUnit(unit);
        assignment.setActive(request.active());

        UserAssignmentEntity saved = assignmentRepository.save(assignment);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!assignmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Назначение не найдено");
        }
        assignmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record AssignmentRequest(
            @NotNull Long userId,
            @NotNull Long unitId,
            boolean active
    ) {
    }
}
