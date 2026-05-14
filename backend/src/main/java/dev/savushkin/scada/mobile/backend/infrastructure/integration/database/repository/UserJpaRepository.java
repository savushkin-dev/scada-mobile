package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    @NonNull Optional<UserEntity> findByCodeAndPassword(@NonNull String code, @NonNull String password);

    @Query("""
            select u
            from UserEntity u
            join fetch u.role r
            where u.id = :userId
            """)
    @NonNull Optional<UserEntity> findByIdWithRole(@Param("userId") Long userId);
}
