package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.projection;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.WorkshopEntity;
import org.springframework.data.rest.core.config.Projection;

/**
 * Projection для UnitEntity: подменяет workshop на полный объект с названием цеха.
 */
@Projection(name = "withWorkshop", types = UnitEntity.class)
public interface UnitWithWorkshopName {
    Long getId();
    String getName();
    String getPrintsrvInstanceId();
    boolean isActive();
    WorkshopEntity getWorkshop();
}
