package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.AssignedUnit;
import dev.savushkin.scada.mobile.backend.domain.model.UserProfile;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Порт доступа к профилю пользователя и его закрепленным аппаратам.
 */
public interface UserProfileRepository {

    @NonNull Optional<UserProfile> findById(long userId);

    @NonNull List<AssignedUnit> findAssignedUnits(long userId);
}
