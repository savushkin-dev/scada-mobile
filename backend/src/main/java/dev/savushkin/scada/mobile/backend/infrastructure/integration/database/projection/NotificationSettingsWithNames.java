package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.projection;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity;
import org.springframework.data.rest.core.config.Projection;

import java.time.LocalDateTime;

/**
 * Projection для UserNotificationSettingsEntity: подменяет user и unit на полные объекты с названиями.
 */
@Projection(name = "withNames", types = UserNotificationSettingsEntity.class)
public interface NotificationSettingsWithNames {
    Long getId();
    UserEntity getUser();
    UnitEntity getUnit();
    boolean isIncidentNotificationsEnabled();
    boolean isAndroidCallNotificationsEnabled();
    boolean isActive();
    LocalDateTime getUpdatedAt();
}
