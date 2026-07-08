package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.RoleJpaRepository;
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

import java.util.List;

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
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserJpaRepository userRepository,
                               RoleJpaRepository roleRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public ResponseEntity<UserEntity> create(@Valid @RequestBody UserRequest request) {
        RoleEntity role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль не найдена"));

        if (userRepository.findByCode(request.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Код сотрудника уже занят");
        }

        UserEntity user = new UserEntity();
        user.setCode(request.code());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setActive(request.active());
        user.setRole(role);

        UserEntity saved = userRepository.save(user);
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
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record UserRequest(
            @NotBlank @Size(max = 10) String code,
            @NotBlank String password,
            @NotBlank String fullName,
            @NotNull Long roleId,
            boolean active
    ) {
    }
}
