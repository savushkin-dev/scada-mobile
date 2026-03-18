package dev.savushkin.scada.mobile.backend.application.ports;

import dev.savushkin.scada.mobile.backend.domain.model.NotificationOutboxEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutboxEntity, Long> {

    @Query("""
        SELECT o
        FROM NotificationOutboxEntity o
        WHERE o.status = :status
          AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now)
        ORDER BY o.createdAt ASC
        """)
    List<NotificationOutboxEntity> findPendingJobs(
        @Param("status") String status,
        @Param("now") Instant now,
        Pageable pageable
    );

}
