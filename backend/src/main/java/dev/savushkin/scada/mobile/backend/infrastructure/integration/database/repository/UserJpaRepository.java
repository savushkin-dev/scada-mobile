package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface UserJpaRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    @RestResource(exported = false)
    @NonNull Optional<UserEntity> findByCode(@NonNull String code);

    @RestResource(exported = false)
    @NonNull List<UserEntity> findTop10ByRole_IdOrderByIdAsc(@NonNull Long roleId);

    @Query("""
            select u
            from UserEntity u
            join fetch u.role r
            where u.id = :userId
            """)
    @NonNull Optional<UserEntity> findByIdWithRole(@Param("userId") Long userId);

    /**
     * Активные сотрудники (кроме роли excludedRole), у которых последняя активность
     * была раньше cutoff. Пользователи без lastActivityAt (NULL) не выбираются.
     */
    @Query("""
            select u
            from UserEntity u
            join fetch u.role r
            where u.active = true
              and r.name <> :excludedRole
              and u.lastActivityAt is not null
              and u.lastActivityAt < :cutoff
            """)
    @RestResource(exported = false)
    @NonNull List<UserEntity> findInactiveSince(@Param("excludedRole") String excludedRole,
                                                @Param("cutoff") LocalDateTime cutoff);
}
