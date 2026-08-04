package dev.savushkin.scada.mobile.backend.infrastructure.scheduler;

import dev.savushkin.scada.mobile.backend.config.AdminBootstrapConfig;
import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationType;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.AdminNotificationJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import dev.savushkin.scada.mobile.backend.services.AdminNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Периодическая проверка длительного бездействия сотрудников.
 * <p>
 * Запускается раз в час и создаёт админ-уведомление {@code USER_INACTIVE} для каждого
 * активного сотрудника (кроме роли ADMIN), чья последняя активность старше порога
 * (по умолчанию 72 часа, свойство {@code admin-notifications.inactivity-threshold-hours}).
 * Сотрудники без метки активности (NULL) пропускаются, чтобы не спамить по старым записям.
 * Уведомление по сотруднику не дублируется, пока предыдущее остаётся непрочитанным.
 */
@Component
public class UserInactivityWatchdogJob {

    private static final Logger log = LoggerFactory.getLogger(UserInactivityWatchdogJob.class);

    private final UserJpaRepository userRepository;
    private final AdminNotificationJpaRepository notificationRepository;
    private final AdminNotificationService notificationService;
    private final long inactivityThresholdHours;

    public UserInactivityWatchdogJob(
            UserJpaRepository userRepository,
            AdminNotificationJpaRepository notificationRepository,
            AdminNotificationService notificationService,
            @Value("${admin-notifications.inactivity-threshold-hours:72}") long inactivityThresholdHours
    ) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.inactivityThresholdHours = inactivityThresholdHours;
    }

    /**
     * Создаёт уведомления о сотрудниках, неактивных дольше порога.
     * <p>
     * Расписание: каждый час в :07.
     */
    @Scheduled(cron = "0 7 * * * ?")
    @Transactional
    public void checkInactiveUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(inactivityThresholdHours);
        log.debug("Starting user inactivity check, thresholdHours={}, cutoff={}", inactivityThresholdHours, cutoff);

        List<UserEntity> inactiveUsers = userRepository.findInactiveSince(AdminBootstrapConfig.ADMIN_ROLE_NAME, cutoff);
        int created = 0;
        for (UserEntity user : inactiveUsers) {
            boolean alreadyNotified = notificationRepository.existsByTypeAndUserIdAndReadFalse(
                    AdminNotificationType.USER_INACTIVE, user.getId());
            if (alreadyNotified) {
                continue;
            }
            notificationService.createUserInactiveNotification(user, inactivityThresholdHours);
            created++;
        }

        log.info("User inactivity check completed, thresholdHours={}, inactiveUsers={}, notificationsCreated={}",
                inactivityThresholdHours, inactiveUsers.size(), created);
    }
}
