package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.projection;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.DeviceTypeEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import org.springframework.data.rest.core.config.Projection;

/**
 * Projection для DeviceEntity: подменяет unit и type на полные объекты с названиями.
 */
@Projection(name = "withNames", types = DeviceEntity.class)
public interface DeviceWithNames {
    Long getId();
    String getCode();
    String getDisplayName();
    UnitEntity getUnit();
    DeviceTypeEntity getType();
}
