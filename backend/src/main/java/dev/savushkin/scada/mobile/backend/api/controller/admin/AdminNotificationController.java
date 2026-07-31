package dev.savushkin.scada.mobile.backend.api.controller.admin;

import dev.savushkin.scada.mobile.backend.api.controller.admin.filter.AdminFilterSupport;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.AdminNotificationEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.AdminNotificationJpaRepository;
import dev.savushkin.scada.mobile.backend.services.AdminNotificationService;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API для управления системными уведомлениями администратора.
 */
@RestController
@RequestMapping("${scada.api.base-path}/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminNotificationController {

    private final AdminNotificationService notificationService;
    private final AdminNotificationJpaRepository notificationRepository;

    public AdminNotificationController(AdminNotificationService notificationService,
                                       AdminNotificationJpaRepository notificationRepository) {
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Список уведомлений.
     * <p>
     * Без параметров — legacy-режим: плоский массив всех уведомлений
     * (используется существующими клиентами).
     * <p>
     * Если переданы параметры фильтрации ({@code q}, {@code f.*}) или
     * пагинации ({@code page}/{@code size}) — возвращает пагинированный
     * результат с фильтрацией на уровне БД и заголовком Content-Range.
     */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam Map<String, String> params,
                                  @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean hasFilters = params.keySet().stream().anyMatch(k ->
                k.equals("q") || k.startsWith("f.") || k.equals("page") || k.equals("size"));
        if (!hasFilters) {
            return ResponseEntity.ok(notificationService.getAllNotifications());
        }

        Page<AdminNotificationEntity> page = notificationRepository.findAll(
                AdminFilterSupport.specification("notifications", params), pageable);
        long start = page.getNumber() * page.getSize();
        long end = Math.min(start + page.getNumberOfElements() - 1, page.getTotalElements() - 1);
        if (end < 0) end = 0;
        return ResponseEntity.ok()
                .header("Content-Range", "notifications " + start + "-" + end + "/" + page.getTotalElements())
                .body(page);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable @NonNull Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }
}
