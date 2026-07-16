package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.domain.auth.EmployeeCredentialsGenerator;
import dev.savushkin.scada.mobile.backend.domain.model.UserAssignmentsChangedEvent;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.RoleJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Application service для сценариев управления доступом сотрудников:
 * создание нового сотрудника со сгенерированным кодом и временным паролем,
 * а также сброс пароля сотруднику.
 */
@Service
public class EmployeeAccessService {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final UnitJpaRepository unitRepository;
    private final UserAssignmentJpaRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

    public EmployeeAccessService(UserJpaRepository userRepository,
                                 RoleJpaRepository roleRepository,
                                 UnitJpaRepository unitRepository,
                                 UserAssignmentJpaRepository assignmentRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuthService authService,
                                 ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Создаёт нового сотрудника с автоматически сгенерированным кодом и временным паролем.
     *
     * @param fullName ФИО
     * @param roleId   ID роли
     * @param active   активен ли сотрудник
     * @param unitIds  список ID автоматов (может быть null)
     * @return результат создания с сгенерированными учётными данными
     */
    @Transactional
    public @NonNull CreatedEmployee createEmployee(@NonNull String fullName,
                                                    long roleId,
                                                    boolean active,
                                                    List<Long> unitIds) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль не найдена"));

        String code = generateUniqueCode();
        String rawPassword = EmployeeCredentialsGenerator.generateTemporaryPassword();

        UserEntity user = new UserEntity();
        user.setCode(code);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setActive(active);
        user.setRole(role);
        user.setPasswordTemporary(true);

        UserEntity saved = userRepository.save(user);
        syncAssignments(saved, unitIds, null);

        eventPublisher.publishEvent(new UserAssignmentsChangedEvent(saved.getId()));

        return new CreatedEmployee(saved.getId(), saved.getCode(), saved.getFullName(),
                role.getId(), saved.isActive(), unitIds, rawPassword);
    }

    /**
     * Сбрасывает пароль сотруднику, генерируя новый временный пароль.
     * Все существующие сессии сотрудника отзываются.
     *
     * @param userId ID сотрудника
     * @return результат сброса с новым временным паролем
     */
    @Transactional
    public @NonNull ResetPassword resetPassword(long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        String rawPassword = EmployeeCredentialsGenerator.generateTemporaryPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPasswordTemporary(true);
        userRepository.save(user);

        authService.revokeAllRefreshTokens(userId);

        return new ResetPassword(user.getCode(), user.getFullName(), rawPassword);
    }

    private @NonNull String generateUniqueCode() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = EmployeeCredentialsGenerator.generateCode();
            if (userRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный код сотрудника");
    }

    private void syncAssignments(UserEntity user, List<Long> unitIds, Long currentUserId) {
        if (unitIds == null) {
            return;
        }

        Set<Long> uniqueUnitIds = new HashSet<>(unitIds);

        for (Long unitId : uniqueUnitIds) {
            assignmentRepository.findByUnit_IdAndActiveTrue(unitId).ifPresent(existing -> {
                Long assignedUserId = existing.getUser().getId();
                if (currentUserId == null || !assignedUserId.equals(currentUserId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Автомат уже закреплён за другим сотрудником");
                }
            });
        }

        assignmentRepository.deleteByUser_Id(user.getId());

        for (Long unitId : uniqueUnitIds) {
            var unit = unitRepository.findById(unitId)
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

    public record CreatedEmployee(
            long id,
            @NonNull String code,
            @NonNull String fullName,
            long roleId,
            boolean active,
            List<Long> unitIds,
            @NonNull String generatedPassword
    ) {
    }

    public record ResetPassword(
            @NonNull String code,
            @NonNull String fullName,
            @NonNull String generatedPassword
    ) {
    }
}
