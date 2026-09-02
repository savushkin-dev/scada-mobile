package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserNotificationSettingsEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface UserNotificationSettingsJpaRepository extends JpaRepository<UserNotificationSettingsEntity, Long> {

    @RestResource(exported = false)
    @NonNull Optional<UserNotificationSettingsEntity> findByUser_IdAndUnit_Id(Long userId, Long unitId);

    @RestResource(exported = false)
    @NonNull Page<UserNotificationSettingsEntity> findByUser_Id(Long userId, Pageable pageable);

    @RestResource(exported = false)
    @NonNull Page<UserNotificationSettingsEntity> findByUnit_Id(Long unitId, Pageable pageable);

    @RestResource(exported = false)
    @NonNull List<UserNotificationSettingsEntity> findByUnit_Id(Long unitId);

    @RestResource(exported = false)
    @Query("""
            select s
            from UserNotificationSettingsEntity s
            join s.unit u
            where s.user.id = :userId
              and u.active = true
            """)
    @NonNull List<UserNotificationSettingsEntity> findByUserId(@Param("userId") Long userId);

    /**
     * Возвращает PrintSrv-идентификаторы активных аппаратов, на уведомления "Вызов"
     * от которых подписан пользователь.
     * <p>
     * Семантика совпадает с {@link dev.savushkin.scada.mobile.backend.services.NotificationSettingsService#getSettingsSnapshot}:
     * отсутствие настройки для пары (user, unit) означает, что оба флага включены
     * (умолчание "Вызов" = true). Если же есть активная настройка, проверяется
     * флаг {@code androidCallNotificationsEnabled}.
     */
    @RestResource(exported = false)
    @Query("""
            select distinct u.printsrvInstanceId
            from UnitEntity u
            left join UserNotificationSettingsEntity s
                   on s.unit.id = u.id and s.user.id = :userId
            where u.active = true
              and u.printsrvInstanceId is not null
              and (
                  s.id is null
                  or (s.active = true and s.androidCallNotificationsEnabled = true)
              )
            """)
    @NonNull Set<String> findAndroidCallEnabledPrintsrvUnitIdsByUserId(@Param("userId") Long userId);

    @RestResource(exported = false)
    void deleteByUser_Id(Long userId);

    @RestResource(exported = false)
    void deleteByUnit_Id(Long unitId);

    @RestResource(exported = false)
    @NonNull List<UserNotificationSettingsEntity> findTop10ByUser_IdOrderByIdAsc(Long userId);

    @RestResource(exported = false)
    @NonNull List<UserNotificationSettingsEntity> findTop10ByUnit_IdOrderByIdAsc(Long unitId);
}
