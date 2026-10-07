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
 * Запускается каждый день в 02:30 ночи и удаляет <b>неактивные</b> записи, созданные
 * (активированные) раньше 24 часов. Активные уведомления (PENDING / IN_PROGRESS) никогда
 * не удаляются: незакрытая задача не должна испаряться из БД, а их удаление без события
 * перехода состояния рассинхронизирует WS-прожекцию ({@code ActiveNotificationStore})
 * с перманентным состоянием — клиенты продолжат получать «фантомные» уведомления,
 * на которые невозможно ответить (accept/complete падает с «не найдено»).
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
     * Удаляет неактивные производственные уведомления старше 24 часов.
     * Активные уведомления не трогаются.
     * <p>
     * Расписание: каждый день в 02:30.
     */
    @Scheduled(cron = "0 30 2 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
        log.debug("Starting production notifications cleanup, cutoff={}", cutoff);
        long deleted = notificationRepository.deleteByActiveFalseAndActivatedAtBefore(cutoff);
        log.info("Production notifications cleanup completed, cutoff={}, deleted={}", cutoff, deleted);
    }
}
