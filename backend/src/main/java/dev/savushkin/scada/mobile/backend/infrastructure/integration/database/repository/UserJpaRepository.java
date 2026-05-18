package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.Optional;


public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    @RestResource(exported = false)
    @NonNull Optional<UserEntity> findByCode(@NonNull String code);

    @Query("""
            select u
            from UserEntity u
            join fetch u.role r
            where u.id = :userId
            """)
    @NonNull Optional<UserEntity> findByIdWithRole(@Param("userId") Long userId);
}
