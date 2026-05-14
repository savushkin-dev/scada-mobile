package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.UserNotificationSettings;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NotificationSettingsRepository {

    @NonNull Optional<UserNotificationSettings> findByUserIdAndUnitId(long userId, long unitId);

    @NonNull List<UserNotificationSettings> findByUserId(long userId);

    @NonNull UserNotificationSettings save(@NonNull UserNotificationSettings settings);

    @NonNull Set<String> findActivePrintSrvUnitIds(long userId);
}
