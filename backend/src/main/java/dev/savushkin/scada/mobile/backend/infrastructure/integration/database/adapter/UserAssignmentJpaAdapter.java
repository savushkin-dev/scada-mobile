package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationSettingsRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * JPA-реализация {@link UserAssignmentRepository} — привязки пользователей к аппаратам из PostgreSQL.
 * <p>
 * Данные о закреплении (кто может отправлять уведомления) хранятся в таблице {@code user_unit_assignments}.
 * Подписка на получение "Вызов"-уведомлений хранится в {@code user_notification_settings}
 * и читается через {@link NotificationSettingsRepository}.
 */
@Component
@Primary
public class UserAssignmentJpaAdapter implements UserAssignmentRepository {

    private final UserAssignmentJpaRepository assignmentRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;

    public UserAssignmentJpaAdapter(UserAssignmentJpaRepository assignmentRepository,
                                      NotificationSettingsRepository notificationSettingsRepository) {
        this.assignmentRepository = assignmentRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
    }

    @Override
    public boolean canSendNotification(long userId, String unitId) {
        return assignmentRepository.existsActiveAssignment(userId, unitId);
    }

    /**
     * Возвращает аппараты, на уведомления "Вызов" от которых подписан работник.
     * <p>
     * Используется для фильтрации WebSocket-рассылок и входящих задач.
     * Не путать с {@link #getAssignedUnitIds()} — закрепление определяет право
     * <i>отправлять</i> уведомление.
     */
    @Override
    public Set<String> getSubscribedUnitIds(long userId) {
        return notificationSettingsRepository.findAndroidCallEnabledPrintSrvUnitIds(userId);
    }

    @Override
    public Set<String> getAssignedUnitIds(long userId) {
        return resolveAssignedPrintSrvIds(userId);
    }

    private Set<String> resolveAssignedPrintSrvIds(long userId) {
        Set<String> result = assignmentRepository.findActiveAssignedPrintsrvIdsByUserId(userId);
        return result == null ? Set.of() : Set.copyOf(result);
    }
}
