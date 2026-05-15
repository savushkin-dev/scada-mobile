package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationSettingsRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UnitQueryRepository;
import dev.savushkin.scada.mobile.backend.application.ports.UserProfileRepository;
import dev.savushkin.scada.mobile.backend.domain.model.UnitNotificationPreference;
import dev.savushkin.scada.mobile.backend.domain.model.UnitSummary;
import dev.savushkin.scada.mobile.backend.domain.model.UserNotificationSettings;
import dev.savushkin.scada.mobile.backend.domain.model.UserProfile;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository settingsRepository;
    private final UnitQueryRepository unitQueryRepository;
    private final UserProfileRepository userProfileRepository;
    private final Clock clock;

    @Autowired
    public NotificationSettingsService(NotificationSettingsRepository settingsRepository,
                                       UnitQueryRepository unitQueryRepository,
                                       UserProfileRepository userProfileRepository) {
        this(settingsRepository, unitQueryRepository, userProfileRepository, Clock.systemUTC());
    }

    public NotificationSettingsService(NotificationSettingsRepository settingsRepository,
                                       UnitQueryRepository unitQueryRepository,
                                       UserProfileRepository userProfileRepository,
                                       Clock clock) {
        this.settingsRepository = settingsRepository;
        this.unitQueryRepository = unitQueryRepository;
        this.userProfileRepository = userProfileRepository;
        this.clock = clock;
    }

    public @NonNull Optional<UserNotificationSettings> findByUserAndUnit(long userId, long unitId) {
        return settingsRepository.findByUserIdAndUnitId(userId, unitId);
    }

    public @NonNull UserNotificationSettings save(@NonNull UserNotificationSettings settings) {
        LocalDateTime updatedAt = LocalDateTime.now(clock);
        return settingsRepository.save(settings.withUpdatedAt(updatedAt));
    }

    public @NonNull Set<String> getAndroidCallEnabledPrintSrvUnitIds(long userId) {
        return settingsRepository.findAndroidCallEnabledPrintSrvUnitIds(userId);
    }

    @Transactional(readOnly = true)
    public @NonNull SettingsSnapshot getSettingsSnapshot(long userId) {
        UserProfile user = loadActiveUser(userId);

        List<UnitSummary> units = unitQueryRepository.findAllActiveUnits();
        Map<Long, UserNotificationSettings> settingsByUnit = new HashMap<>();
        for (UserNotificationSettings settings : settingsRepository.findByUserId(user.id())) {
            settingsByUnit.put(settings.unitId(), settings);
        }

        List<UnitNotificationPreference> preferences = units.stream()
                .sorted(Comparator.comparingLong(UnitSummary::unitId))
                .map(unit -> buildPreference(unit, settingsByUnit.get(unit.unitId())))
                .toList();

        String etag = computeSettingsEtag(preferences);
        return new SettingsSnapshot(preferences, etag);
    }

    @Transactional
    public void updateSettings(long userId, long unitId, boolean techEnabled, boolean masterEnabled) {
        UserProfile user = loadActiveUser(userId);

        UnitSummary unit = unitQueryRepository.findActiveUnitById(unitId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Аппарат не найден"));

        UserNotificationSettings current = settingsRepository.findByUserIdAndUnitId(user.id(), unit.unitId())
                .orElse(null);

        UserNotificationSettings updated = new UserNotificationSettings(
                current == null ? null : current.id(),
                user.id(),
                unit.unitId(),
                techEnabled,
                masterEnabled,
                true,
                LocalDateTime.now(clock)
        );

        settingsRepository.save(updated);
    }

    private UserProfile loadActiveUser(long userId) {
        UserProfile user = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Пользователь не найден"));
        if (!user.active()) {
            throw new ResponseStatusException(FORBIDDEN, "Пользователь заблокирован");
        }
        return user;
    }

    private UnitNotificationPreference buildPreference(UnitSummary unit, UserNotificationSettings settings) {
        if (settings == null) {
            return new UnitNotificationPreference(unit.unitId(), unit.unitName(), true, true);
        }
        if (!settings.active()) {
            return new UnitNotificationPreference(unit.unitId(), unit.unitName(), false, false);
        }
        return new UnitNotificationPreference(
                unit.unitId(),
                unit.unitName(),
                settings.incidentNotificationsEnabled(),
                settings.androidCallNotificationsEnabled()
        );
    }

    private String computeSettingsEtag(List<UnitNotificationPreference> preferences) {
        StringBuilder sb = new StringBuilder();
        for (UnitNotificationPreference pref : preferences) {
            sb.append(pref.unitId())
                    .append(':').append(pref.techEnabled())
                    .append(':').append(pref.masterEnabled())
                    .append(';');
        }
        return sha256(sb.toString());
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record SettingsSnapshot(
            List<UnitNotificationPreference> preferences,
            String etag
    ) {
    }
}
