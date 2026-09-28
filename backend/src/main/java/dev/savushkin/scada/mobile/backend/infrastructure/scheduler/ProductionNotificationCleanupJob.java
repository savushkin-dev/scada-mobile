package dev.savushkin.scada.mobile.backend.infrastructure.scheduler;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.ProductionNotificationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Периодическое удаление старых производственных уведомлений.
 * <p>
 * Запускается каждый день в 02:30 ночи и удаляет записи, созданные
 * (активированные) раньше 24 часов. Это предотвращает бесконечный рост
 * таблицы {@code production_notifications}, поскольку история отправленных
 * и принятых задач накапливается быстро.
 */
@Component
public class ProductionNotificationCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ProductionNotificationCleanupJob.class);

    /** Время жизни записи в часах. */
    private static final long RETENTION_HOURS = 24;

    private final ProductionNotificationJpaRepository notificationRepository;

    public ProductionNotificationCleanupJob(ProductionNotificationJpaRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Удаляет производственные уведомления старше 24 часов.
     * <p>
     * Расписание: каждый день в 02:30.
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
        log.debug("Starting production notifications cleanup, cutoff={}", cutoff);
        long deleted = notificationRepository.deleteByActivatedAtBefore(cutoff);
        log.info("Production notifications cleanup completed, cutoff={}, deleted={}", cutoff, deleted);
    }
}
