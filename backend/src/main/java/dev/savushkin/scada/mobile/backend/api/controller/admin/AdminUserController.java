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
import java.util.Set;

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
        syncAssignments(saved, request.unitIds(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserEntity> update(@PathVariable @NonNull Long id,
                                             @Valid @RequestBody UserRequest request) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль не найдена"));

        if (!user.getCode().equals(request.code()) && userRepository.findByCode(request.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Код сотрудника уже занят");
        }

        user.setCode(request.code());
        user.setFullName(request.fullName());
        user.setActive(request.active());
        user.setRole(role);

        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        UserEntity saved = userRepository.save(user);
        syncAssignments(saved, request.unitIds(), id);
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

    /**
     * Синхронизирует назначения пользователя на автоматы.
     *
     * @param user          пользователь
     * @param unitIds       желаемый список ID автоматов (null — не менять)
     * @param currentUserId ID текущего пользователя для исключения при проверке конфликтов
     *                        (null для create)
     */
    private void syncAssignments(UserEntity user, List<Long> unitIds, Long currentUserId) {
        if (unitIds == null) {
            return;
        }

        Set<Long> uniqueUnitIds = new HashSet<>(unitIds);

        // Валидация: каждый автомат может быть назначен только одному сотруднику
        for (Long unitId : uniqueUnitIds) {
            assignmentRepository.findByUnit_IdAndActiveTrue(unitId).ifPresent(existing -> {
                Long assignedUserId = existing.getUser().getId();
                if (currentUserId == null || !assignedUserId.equals(currentUserId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Автомат уже закреплён за другим сотрудником");
                }
            });
        }

        // Удалить старые назначения
        assignmentRepository.deleteByUser_Id(user.getId());

        // Создать новые
        for (Long unitId : uniqueUnitIds) {
            UnitEntity unit = unitRepository.findById(unitId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Автомат не найден: " + unitId));

            UserAssignmentEntity assignment = new UserAssignmentEntity();
            assignment.setUser(user);
            assignment.setUnit(unit);
            assignment.setAssignedAt(LocalDateTime.now());
            assignment.setActive(true);
            assignmentRepository.save(assignment);
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
