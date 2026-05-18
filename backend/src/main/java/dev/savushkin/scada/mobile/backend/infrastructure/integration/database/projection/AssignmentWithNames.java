package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.projection;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UnitEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import org.springframework.data.rest.core.config.Projection;

import java.time.LocalDateTime;

/**
 * Projection для UserAssignmentEntity: подменяет user и unit на полные объекты с названиями.
 */
@Projection(name = "withNames", types = UserAssignmentEntity.class)
public interface AssignmentWithNames {
    Long getId();
    UserEntity getUser();
    UnitEntity getUnit();
    LocalDateTime getAssignedAt();
    boolean isActive();
}
