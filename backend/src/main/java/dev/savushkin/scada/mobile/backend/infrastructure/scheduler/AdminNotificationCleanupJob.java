package dev.savushkin.scada.mobile.backend.infrastructure.scheduler;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.AdminNotificationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Периодическое удаление прочитанных админ-уведомлений из БД.
 * <p>
 * Запускается раз в день (02:30 ночи) и удаляет уведомления, отмеченные
 * прочитанными и созданные раньше retention-периода (по умолчанию 60 дней,
 * свойство {@code admin-notifications.read-retention-days}). Это предотвращает
 * бесконечный рост таблицы admin_notifications.
 */
@Component
public class AdminNotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AdminNotificationCleanupJob.class);

    private final AdminNotificationJpaRepository notificationRepository;
    private final long readRetentionDays;

    public AdminNotificationCleanupJob(
            AdminNotificationJpaRepository notificationRepository,
            @Value("${admin-notifications.read-retention-days:60}") long readRetentionDays
    ) {
        this.notificationRepository = notificationRepository;
        this.readRetentionDays = readRetentionDays;
    }

    /**
     * Удаляет прочитанные уведомления старше retention-периода.
     * <p>
     * Расписание: каждый день в 02:30.
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional
    public void cleanupReadNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(readRetentionDays);
        log.debug("Starting read admin notifications cleanup, cutoff={}", cutoff);
        long deleted = notificationRepository.deleteByReadTrueAndCreatedAtBefore(cutoff);
        log.info("Read admin notifications cleanup completed, cutoff={}, deleted={}", cutoff, deleted);
    }
}
