package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationSettingsRepository;
import dev.savushkin.scada.mobile.backend.domain.model.UserNotificationSettings;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository settingsRepository;
    private final Clock clock;

    @Autowired
    public NotificationSettingsService(NotificationSettingsRepository settingsRepository) {
        this(settingsRepository, Clock.systemUTC());
    }

    public NotificationSettingsService(NotificationSettingsRepository settingsRepository, Clock clock) {
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    public @NonNull Optional<UserNotificationSettings> findByUserAndUnit(long userId, long unitId) {
        return settingsRepository.findByUserIdAndUnitId(userId, unitId);
    }

    public @NonNull UserNotificationSettings save(@NonNull UserNotificationSettings settings) {
        LocalDateTime updatedAt = LocalDateTime.now(clock);
        return settingsRepository.save(settings.withUpdatedAt(updatedAt));
    }

    public @NonNull Set<String> getActivePrintSrvUnitIds(long userId) {
        return settingsRepository.findActivePrintSrvUnitIds(userId);
    }
}
