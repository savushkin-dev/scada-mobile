package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserNotificationSettingsJpaRepository;
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
 * Ручной CRUD-контроллер для управления настройками уведомлений пользователей.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/user-notification-settings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationSettingsController {

    private final UserNotificationSettingsJpaRepository settingsRepository;
    private final UserJpaRepository userRepository;
    private final UnitJpaRepository unitRepository;

    public AdminNotificationSettingsController(UserNotificationSettingsJpaRepository settingsRepository,
                                               UserJpaRepository userRepository,
                                               UnitJpaRepository unitRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.unitRepository = unitRepository;
    }

    @PostMapping
    public ResponseEntity<UserNotificationSettingsEntity> create(@Valid @RequestBody NotificationSettingsRequest request) {
        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пользователь не найден"));
        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));

        UserNotificationSettingsEntity settings = new UserNotificationSettingsEntity();
        settings.setUser(user);
        settings.setUnit(unit);
        settings.setIncidentNotificationsEnabled(request.incidentNotificationsEnabled());
        settings.setAndroidCallNotificationsEnabled(request.androidCallNotificationsEnabled());
        settings.setActive(request.active());
        settings.setUpdatedAt(LocalDateTime.now());

        UserNotificationSettingsEntity saved = settingsRepository.save(settings);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserNotificationSettingsEntity> update(@PathVariable @NonNull Long id,
                                                                 @Valid @RequestBody NotificationSettingsRequest request) {
        UserNotificationSettingsEntity settings = settingsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Настройки не найдены"));

        UserEntity user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пользователь не найден"));
        UnitEntity unit = unitRepository.findById(request.unitId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Аппарат не найден"));

        settings.setUser(user);
        settings.setUnit(unit);
        settings.setIncidentNotificationsEnabled(request.incidentNotificationsEnabled());
        settings.setAndroidCallNotificationsEnabled(request.androidCallNotificationsEnabled());
        settings.setActive(request.active());
        settings.setUpdatedAt(LocalDateTime.now());

        UserNotificationSettingsEntity saved = settingsRepository.save(settings);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NonNull Long id) {
        if (!settingsRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Настройки не найдены");
        }
        settingsRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record NotificationSettingsRequest(
            @NotNull Long userId,
            @NotNull Long unitId,
            boolean incidentNotificationsEnabled,
            boolean androidCallNotificationsEnabled,
            boolean active
    ) {
    }
}
