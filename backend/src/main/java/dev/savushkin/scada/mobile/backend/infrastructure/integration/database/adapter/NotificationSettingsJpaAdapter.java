package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.adapter;

import dev.savushkin.scada.mobile.backend.application.ports.NotificationSettingsRepository;
import dev.savushkin.scada.mobile.backend.domain.model.UserNotificationSettings;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UserNotificationSettingsJpaRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.Set;

@Component
public class NotificationSettingsJpaAdapter implements NotificationSettingsRepository {

    private final UserNotificationSettingsJpaRepository settingsRepository;
    private final EntityManager entityManager;

    public NotificationSettingsJpaAdapter(UserNotificationSettingsJpaRepository settingsRepository,
                                          EntityManager entityManager) {
        this.settingsRepository = settingsRepository;
        this.entityManager = entityManager;
    }

    @Override
    public @NonNull Optional<UserNotificationSettings> findByUserIdAndUnitId(long userId, long unitId) {
        return settingsRepository.findByUser_IdAndUnit_Id(userId, unitId)
                .map(this::toDomain);
    }

    @Override
    public @NonNull UserNotificationSettings save(@NonNull UserNotificationSettings settings) {
        UserNotificationSettingsEntity entity = settingsRepository
                .findByUser_IdAndUnit_Id(settings.userId(), settings.unitId())
                .orElseGet(UserNotificationSettingsEntity::new);

        if (entity.getId() == null) {
            UserEntity userRef = entityManager.getReference(UserEntity.class, settings.userId());
            UnitEntity unitRef = entityManager.getReference(UnitEntity.class, settings.unitId());
            entity.setUser(userRef);
            entity.setUnit(unitRef);
        }

        entity.setSystemSoundEnabled(settings.systemSoundEnabled());
        entity.setSystemVibrationEnabled(settings.systemVibrationEnabled());
        entity.setAndroidPushEnabled(settings.androidPushEnabled());
        entity.setActive(settings.active());
        entity.setUpdatedAt(settings.updatedAt());

        UserNotificationSettingsEntity saved = settingsRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public @NonNull Set<String> findActivePrintSrvUnitIds(long userId) {
        Set<String> result = settingsRepository.findActivePrintsrvUnitIdsByUserId(userId);
        return result == null ? Set.of() : Set.copyOf(result);
    }

    private UserNotificationSettings toDomain(UserNotificationSettingsEntity entity) {
        return new UserNotificationSettings(
                entity.getId(),
                entity.getUser().getId(),
                entity.getUnit().getId(),
                entity.isSystemSoundEnabled(),
                entity.isSystemVibrationEnabled(),
                entity.isAndroidPushEnabled(),
                entity.isActive(),
                entity.getUpdatedAt()
        );
    }
}
