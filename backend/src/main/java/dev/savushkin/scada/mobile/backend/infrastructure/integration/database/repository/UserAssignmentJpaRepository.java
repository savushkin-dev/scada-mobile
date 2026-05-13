package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.UserAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface UserAssignmentJpaRepository extends JpaRepository<UserAssignmentEntity, Long> {

    @Query("""
            select (count(a) > 0)
            from UserAssignmentEntity a
            join a.unit u
            where a.user.id = :userId
              and a.active = true
              and u.active = true
              and u.printsrvInstanceId = :printsrvInstanceId
            """)
    boolean existsActiveAssignment(@Param("userId") Long userId,
                                   @Param("printsrvInstanceId") String printsrvInstanceId);

    @Query("""
            select distinct u.printsrvInstanceId
            from UserAssignmentEntity a
            join a.unit u
            where a.user.id = :userId
              and a.active = true
              and u.active = true
              and u.printsrvInstanceId is not null
            """)
    Set<String> findActiveAssignedPrintsrvIdsByUserId(@Param("userId") Long userId);
}
