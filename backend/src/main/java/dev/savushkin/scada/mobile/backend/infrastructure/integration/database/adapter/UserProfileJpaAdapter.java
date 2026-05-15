package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.UserProfileRepository;
import dev.savushkin.scada.mobile.backend.domain.model.AssignedUnit;
import dev.savushkin.scada.mobile.backend.domain.model.UserProfile;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.AssignedUnitProjection;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserAssignmentJpaRepository;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class UserProfileJpaAdapter implements UserProfileRepository {

    private final UserJpaRepository userRepository;
    private final UserAssignmentJpaRepository assignmentRepository;

    public UserProfileJpaAdapter(UserJpaRepository userRepository,
                                 UserAssignmentJpaRepository assignmentRepository) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public @NonNull Optional<UserProfile> findById(long userId) {
        return userRepository.findByIdWithRole(userId)
                .map(this::toDomain);
    }

    @Override
    public @NonNull List<AssignedUnit> findAssignedUnits(long userId) {
        return assignmentRepository.findActiveAssignedUnitsByUserId(userId)
                .stream()
                .map(this::toAssignedUnit)
                .toList();
    }

    private UserProfile toDomain(UserEntity entity) {
        return new UserProfile(
                entity.getId(),
                entity.getCode(),
                entity.getFullName(),
                entity.getRole().getName(),
                entity.isActive(),
                List.of()
        );
    }

    private AssignedUnit toAssignedUnit(AssignedUnitProjection projection) {
        return new AssignedUnit(
                projection.getUnitId(),
                projection.getUnitName(),
                projection.getPrintsrvInstanceId()
        );
    }
}
