package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.AdminNotificationEntity;
import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationEvent;
import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationSeverity;
import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationType;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.AdminNotificationJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис управления системными уведомлениями для администратора.
 * <p>
 * Уведомления создаются при автоматических событиях (например, обнаружение нового устройства)
 * и рассылаются администраторам через WebSocket.
 */
@Service
public class AdminNotificationService {

    private final AdminNotificationJpaRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminNotificationService(AdminNotificationJpaRepository notificationRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void createDeviceDiscoveredNotification(@NonNull String instanceId, @NonNull String deviceCode) {
        AdminNotificationEntity notification = new AdminNotificationEntity();
        notification.setType(AdminNotificationType.DEVICE_DISCOVERED);
        notification.setSeverity(AdminNotificationSeverity.INFO);
        notification.setInstanceId(instanceId);
        notification.setDeviceCode(deviceCode);
        notification.setMessage(String.format(
                "Обнаружено новое устройство '%s' на аппарате '%s'. " +
                        "Установите тип и отображаемое имя в панели администратора.",
                deviceCode, instanceId));
        notification.setRead(false);

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new AdminNotificationEvent(notification));
    }

    @Transactional
    public void createDeviceDisconnectedNotification(@NonNull String instanceId, @NonNull String deviceCode) {
        AdminNotificationEntity notification = new AdminNotificationEntity();
        notification.setType(AdminNotificationType.DEVICE_DISCONNECTED);
        notification.setSeverity(AdminNotificationSeverity.WARNING);
        notification.setInstanceId(instanceId);
        notification.setDeviceCode(deviceCode);
        notification.setMessage(String.format(
                "Устройство '%s' на аппарате '%s' отключено (отсутствует в runtime). " +
                        "Проверьте подключение или удалите устройство из конфигурации.",
                deviceCode, instanceId));
        notification.setRead(false);

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new AdminNotificationEvent(notification));
    }

    @Transactional
    public void createDeviceReconnectedNotification(@NonNull String instanceId, @NonNull String deviceCode) {
        AdminNotificationEntity notification = new AdminNotificationEntity();
        notification.setType(AdminNotificationType.DEVICE_RECONNECTED);
        notification.setSeverity(AdminNotificationSeverity.INFO);
        notification.setInstanceId(instanceId);
        notification.setDeviceCode(deviceCode);
        notification.setMessage(String.format(
                "Устройство '%s' на аппарате '%s' снова подключено (появилось в runtime).",
                deviceCode, instanceId));
        notification.setRead(false);

        notificationRepository.save(notification);
        eventPublisher.publishEvent(new AdminNotificationEvent(notification));
    }

    @NonNull
    public List<AdminNotificationEntity> getUnreadNotifications() {
        return notificationRepository.findByReadFalseOrderByCreatedAtDesc();
    }

    public long getUnreadCount() {
        return notificationRepository.countByReadFalse();
    }

    @Transactional
    public void markAsRead(long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllAsRead() {
        List<AdminNotificationEntity> unread = notificationRepository.findByReadFalseOrderByCreatedAtDesc();
        for (AdminNotificationEntity n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
    }
}
