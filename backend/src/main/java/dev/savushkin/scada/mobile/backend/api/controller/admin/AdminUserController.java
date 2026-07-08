package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.RoleJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ручной CRUD-контроллер для управления пользователями.
 * <p>
 * Spring Data REST экспортирует только чтение (GET /users, GET /users/{id}).
 * Создание, обновление, удаление — через этот контроллер с валидацией
 * и автоматическим хешированием пароля.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final UnitJpaRepository unitRepository;
    private final UserAssignmentJpaRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserJpaRepository userRepository,
                               RoleJpaRepository roleRepository,
                               UnitJpaRepository unitRepository,
                               UserAssignmentJpaRepository assignmentRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public ResponseEntity<UserEntity> create(@Valid @RequestBody UserRequest request) {
        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль не найдена"));

        if (userRepository.findByCode(request.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Код сотрудника уже занят");
        }

        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль обязателен");
        }

        UserEntity user = new UserEntity();
        user.setCode(request.code());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setActive(request.active());
        user.setRole(role);

        UserEntity saved = userRepository.save(user);

        syncAssignments(saved, request.unitIds());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserEntity> update(@PathVariable @NonNull Long id,
                                             @Valid @RequestBody UserRequest request) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль не найдена"));

        user.setCode(request.code());
        user.setFullName(request.fullName());
        user.setActive(request.active());
        user.setRole(role);

        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        UserEntity saved = userRepository.save(user);

        syncAssignments(saved, request.unitIds());

        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
        assignmentRepository.deleteByUser_Id(id);
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void syncAssignments(UserEntity user, List<Long> unitIds) {
        if (unitIds == null) {
            return;
        }

        List<UserAssignmentEntity> current = assignmentRepository.findByUser_Id(user.getId());
        Set<Long> currentUnitIds = current.stream()
                .map(a -> a.getUnit().getId())
                .collect(Collectors.toSet());
        Set<Long> newUnitIds = new HashSet<>(unitIds);

        // Проверка конфликтов: для каждого нового unitId, который не в current
        for (Long unitId : newUnitIds) {
            if (currentUnitIds.contains(unitId)) {
                continue;
            }
            Optional<UserAssignmentEntity> existing = assignmentRepository.findByUnit_IdAndActiveTrue(unitId);
            if (existing.isPresent()) {
                Long assignedUserId = existing.get().getUser().getId();
                if (!assignedUserId.equals(user.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Автомат уже закреплён за сотрудником: " + existing.get().getUser().getFullName());
                }
            }
        }

        // Удалить те, которых нет в новом списке
        current.stream()
                .filter(a -> !newUnitIds.contains(a.getUnit().getId()))
                .forEach(assignmentRepository::delete);

        // Добавить новые
        for (Long unitId : newUnitIds) {
            if (!currentUnitIds.contains(unitId)) {
                UnitEntity unit = unitRepository.findById(unitId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));
                UserAssignmentEntity a = new UserAssignmentEntity();
                a.setUser(user);
                a.setUnit(unit);
                a.setActive(true);
                a.setAssignedAt(LocalDateTime.now());
                assignmentRepository.save(a);
            }
        }
    }

    public record UserRequest(
            @NotBlank @Size(max = 10) String code,
            String password,
            @NotBlank String fullName,
            @NotNull Long roleId,
            boolean active,
            List<Long> unitIds
    ) {
    }
}
