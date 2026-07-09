package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.*;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.*;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Единый read-only контроллер для админ-панели.
 * <p>
 * Spring Data REST конфликтует с ручными @RestController на том же пути,
 * поэтому чтение (GET /list, GET /{id}) делается через этот контроллер,
 * а запись (POST/PUT/DELETE) — через отдельные контроллеры.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReadController {

    private final RoleJpaRepository roleRepository;
    private final WorkshopJpaRepository workshopRepository;
    private final DeviceTypeJpaRepository deviceTypeRepository;
    private final UnitJpaRepository unitRepository;
    private final DeviceJpaRepository deviceRepository;
    private final UserJpaRepository userRepository;
    private final UserAssignmentJpaRepository assignmentRepository;
    private final UserNotificationSettingsJpaRepository settingsRepository;

    public AdminReadController(RoleJpaRepository roleRepository,
                               WorkshopJpaRepository workshopRepository,
                               DeviceTypeJpaRepository deviceTypeRepository,
                               UnitJpaRepository unitRepository,
                               DeviceJpaRepository deviceRepository,
                               UserJpaRepository userRepository,
                               UserAssignmentJpaRepository assignmentRepository,
                               UserNotificationSettingsJpaRepository settingsRepository) {
        this.roleRepository = roleRepository;
        this.workshopRepository = workshopRepository;
        this.deviceTypeRepository = deviceTypeRepository;
        this.unitRepository = unitRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.settingsRepository = settingsRepository;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static <T> ResponseEntity<Page<T>> pageResponse(Page<T> page, String resource) {
        long start = page.getNumber() * page.getSize();
        long end = Math.min(start + page.getNumberOfElements() - 1, page.getTotalElements() - 1);
        if (end < 0) end = 0;
        String range = resource + " " + start + "-" + end + "/" + page.getTotalElements();
        return ResponseEntity.ok()
                .header("Content-Range", range)
                .body(page);
    }

    // ── Roles ─────────────────────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<Page<RoleEntity>> listRoles(Pageable pageable) {
        return pageResponse(roleRepository.findAll(pageable), "roles");
    }

    @GetMapping("/roles/{id}")
    public ResponseEntity<RoleEntity> getRole(@PathVariable @NonNull Long id) {
        return roleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Workshops ─────────────────────────────────────────────────────────

    @GetMapping("/workshops")
    public ResponseEntity<Page<WorkshopEntity>> listWorkshops(Pageable pageable) {
        return pageResponse(workshopRepository.findAll(pageable), "workshops");
    }

    @GetMapping("/workshops/{id}")
    public ResponseEntity<WorkshopEntity> getWorkshop(@PathVariable @NonNull Long id) {
        return workshopRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Device Types ──────────────────────────────────────────────────────

    @GetMapping("/device-types")
    public ResponseEntity<Page<DeviceTypeEntity>> listDeviceTypes(Pageable pageable) {
        return pageResponse(deviceTypeRepository.findAll(pageable), "device-types");
    }

    @GetMapping("/device-types/{id}")
    public ResponseEntity<DeviceTypeEntity> getDeviceType(@PathVariable @NonNull Long id) {
        return deviceTypeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Units ─────────────────────────────────────────────────────────────

    @GetMapping("/units")
    public ResponseEntity<Page<UnitEntity>> listUnits(Pageable pageable) {
        return pageResponse(unitRepository.findAll(pageable), "units");
    }

    @GetMapping("/units/{id}")
    public ResponseEntity<UnitEntity> getUnit(@PathVariable @NonNull Long id) {
        return unitRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Devices ───────────────────────────────────────────────────────────

    @GetMapping("/devices")
    public ResponseEntity<Page<DeviceEntity>> listDevices(
            @RequestParam(required = false) Long unitId,
            Pageable pageable
    ) {
        if (unitId != null) {
            List<DeviceEntity> devices = deviceRepository.findByUnit_Id(unitId);
            Page<DeviceEntity> page = new org.springframework.data.domain.PageImpl<>(devices, pageable, devices.size());
            return pageResponse(page, "devices");
        }
        return pageResponse(deviceRepository.findAll(pageable), "devices");
    }

    @GetMapping("/devices/{id}")
    public ResponseEntity<DeviceEntity> getDevice(@PathVariable @NonNull Long id) {
        return deviceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Users ─────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<Page<UserEntity>> listUsers(Pageable pageable) {
        return pageResponse(userRepository.findAll(pageable), "users");
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserEntity> getUser(@PathVariable @NonNull Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── User Assignments ──────────────────────────────────────────────────

    @GetMapping("/user-assignments")
    public ResponseEntity<Page<UserAssignmentEntity>> listAssignments(Pageable pageable) {
        return pageResponse(assignmentRepository.findAll(pageable), "user-assignments");
    }

    @GetMapping("/user-assignments/{id}")
    public ResponseEntity<UserAssignmentEntity> getAssignment(@PathVariable @NonNull Long id) {
        return assignmentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Notification Settings ─────────────────────────────────────────────

    @GetMapping("/user-notification-settings")
    public ResponseEntity<Page<UserNotificationSettingsEntity>> listSettings(Pageable pageable) {
        return pageResponse(settingsRepository.findAll(pageable), "user-notification-settings");
    }

    @GetMapping("/user-notification-settings/{id}")
    public ResponseEntity<UserNotificationSettingsEntity> getSettings(@PathVariable @NonNull Long id) {
        return settingsRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
