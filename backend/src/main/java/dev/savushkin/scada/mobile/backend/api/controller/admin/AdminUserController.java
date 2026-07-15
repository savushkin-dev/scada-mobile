package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.api.dto.admin.PasswordResetResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.admin.UserCreateResponseDTO;
import dev.savushkin.scada.mobile.backend.exception.UnitAssignmentConflictException;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.RoleJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import dev.savushkin.scada.mobile.backend.services.EmployeeAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
 * Создание, обновление, удаление — через этот контроллер с валидацией.
 * Код и пароль генерируются системой автоматически; администратор не задаёт их вручную.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final UnitJpaRepository unitRepository;
    private final UserAssignmentJpaRepository assignmentRepository;
    private final EmployeeAccessService employeeAccessService;

    public AdminUserController(UserJpaRepository userRepository,
                               RoleJpaRepository roleRepository,
                               UnitJpaRepository unitRepository,
                               UserAssignmentJpaRepository assignmentRepository,
                               EmployeeAccessService employeeAccessService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeAccessService = employeeAccessService;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UserCreateResponseDTO> create(@Valid @RequestBody UserCreateRequest request) {
        EmployeeAccessService.CreatedEmployee created = employeeAccessService.createEmployee(
                request.fullName(), request.roleId(), request.active(), request.unitIds()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(new UserCreateResponseDTO(
                created.id(),
                created.code(),
                created.fullName(),
                created.roleId(),
                created.active(),
                created.unitIds(),
                created.generatedPassword()
        ));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<UserEntity> update(@PathVariable @NonNull Long id,
                                             @Valid @RequestBody UserUpdateRequest request) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль не найдена"));

        user.setFullName(request.fullName());
        user.setActive(request.active());
        user.setRole(role);

        UserEntity saved = userRepository.save(user);
        syncAssignments(saved, request.unitIds(), id);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
        assignmentRepository.deleteByUser_Id(id);
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @Transactional
    public ResponseEntity<PasswordResetResponseDTO> resetPassword(@PathVariable @NonNull Long id) {
        EmployeeAccessService.ResetPassword reset = employeeAccessService.resetPassword(id);
        return ResponseEntity.ok(new PasswordResetResponseDTO(
                reset.code(), reset.fullName(), reset.generatedPassword()
        ));
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
                    throw new UnitAssignmentConflictException("unitIds",
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

    public record UserCreateRequest(
            @NotBlank String fullName,
            @NotNull Long roleId,
            boolean active,
            List<Long> unitIds
    ) {
    }

    public record UserUpdateRequest(
            @NotBlank String fullName,
            @NotNull Long roleId,
            boolean active,
            List<Long> unitIds
    ) {
    }
}
