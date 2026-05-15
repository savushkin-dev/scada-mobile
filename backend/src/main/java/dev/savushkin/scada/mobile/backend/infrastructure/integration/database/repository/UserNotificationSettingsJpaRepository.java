package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserNotificationSettingsJpaRepository extends JpaRepository<UserNotificationSettingsEntity, Long> {

    @NonNull Optional<UserNotificationSettingsEntity> findByUser_IdAndUnit_Id(Long userId, Long unitId);

    @Query("""
            select s
            from UserNotificationSettingsEntity s
            join s.unit u
            where s.user.id = :userId
              and u.active = true
            """)
    @NonNull List<UserNotificationSettingsEntity> findByUserId(@Param("userId") Long userId);

    @Query("""
            select distinct u.printsrvInstanceId
            from UserNotificationSettingsEntity s
            join s.unit u
            where s.user.id = :userId
              and s.active = true
              and s.androidCallNotificationsEnabled = true
              and u.active = true
              and u.printsrvInstanceId is not null
            """)
    @NonNull Set<String> findAndroidCallEnabledPrintsrvUnitIdsByUserId(@Param("userId") Long userId);
}
