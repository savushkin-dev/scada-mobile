package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * JPA-реализация {@link UserAssignmentRepository} — привязки пользователей к аппаратам из PostgreSQL.
 * <p>
 * Данные хранятся в таблице {@code user_unit_assignments}.
 * Бизнес-логика в {@code NotificationService} не зависит от конкретной реализации порта.
 */
@Component
@Primary
public class UserAssignmentJpaAdapter implements UserAssignmentRepository {

    private final UserAssignmentJpaRepository assignmentRepository;
    private final UnitJpaRepository unitRepository;

    public UserAssignmentJpaAdapter(UserAssignmentJpaRepository assignmentRepository,
                                    UnitJpaRepository unitRepository) {
        this.assignmentRepository = assignmentRepository;
        this.unitRepository = unitRepository;
    }

    @Override
    public boolean canSendNotification(long userId, String unitId) {
        return assignmentRepository.existsActiveAssignment(userId, unitId);
    }

    @Override
    public Set<String> getSubscribedUnitIds(long userId) {
        return resolveAssignedPrintSrvIds(userId);
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
