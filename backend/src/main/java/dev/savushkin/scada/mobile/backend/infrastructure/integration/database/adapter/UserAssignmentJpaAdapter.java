package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.UserAssignmentRepository;
import dev.savushkin.scada.mobile.backend.config.NotificationProperties;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Primary
public class UserAssignmentJpaAdapter implements UserAssignmentRepository {

    private final UserAssignmentJpaRepository assignmentRepository;
    private final UnitJpaRepository unitRepository;
    private final NotificationProperties notificationProperties;

    public UserAssignmentJpaAdapter(UserAssignmentJpaRepository assignmentRepository,
                                    UnitJpaRepository unitRepository,
                                    NotificationProperties notificationProperties) {
        this.assignmentRepository = assignmentRepository;
        this.unitRepository = unitRepository;
        this.notificationProperties = notificationProperties;
    }

    @Override
    public boolean canSendNotification(long userId, String unitId) {
        if (notificationProperties.isDefaultAccess()) {
            return true;
        }
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
        Set<String> result;
        if (notificationProperties.isDefaultAccess()) {
            result = unitRepository.findAllActivePrintsrvInstanceIds();
        } else {
            result = assignmentRepository.findActiveAssignedPrintsrvIdsByUserId(userId);
        }
        return result == null ? Set.of() : Set.copyOf(result);
    }
}
