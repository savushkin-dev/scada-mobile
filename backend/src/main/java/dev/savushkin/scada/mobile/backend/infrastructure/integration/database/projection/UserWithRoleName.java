package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.projection;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.RoleEntity;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import org.springframework.data.rest.core.config.Projection;

/**
 * Projection для UserEntity: подменяет role на полный объект с названием роли.
 */
@Projection(name = "withRole", types = UserEntity.class)
public interface UserWithRoleName {
    Long getId();
    String getCode();
    String getFullName();
    boolean isActive();
    RoleEntity getRole();
}
