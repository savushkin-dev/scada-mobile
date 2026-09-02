package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

import dev.savushkin.scada.mobile.backend.domain.model.NotificationStatus;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity.ProductionNotificationEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RestResource;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductionNotificationJpaRepository extends JpaRepository<ProductionNotificationEntity, Long> {

    @RestResource(exported = false)
    @NonNull Optional<ProductionNotificationEntity> findByUnitIdAndActiveTrue(@NonNull Long unitId);

    @RestResource(exported = false)
    @NonNull List<ProductionNotificationEntity> findAllByActiveTrue();

    @RestResource(exported = false)
    @NonNull List<ProductionNotificationEntity> findAllByCreatorIdOrderByActivatedAtDesc(@NonNull String creatorId);

    @RestResource(exported = false)
    @NonNull List<ProductionNotificationEntity> findAllByAcceptedByOrderByAcceptedAtDesc(@NonNull String acceptedBy);

    @RestResource(exported = false)
    @NonNull Page<ProductionNotificationEntity> findAllByCreatorIdAndStatusInOrderByActivatedAtDesc(
            @NonNull String creatorId,
            @NonNull Collection<NotificationStatus> statuses,
            @NonNull Pageable pageable);

    @RestResource(exported = false)
    @NonNull Page<ProductionNotificationEntity> findAllByAcceptedByAndStatusInOrderByAcceptedAtDesc(
            @NonNull String acceptedBy,
            @NonNull Collection<NotificationStatus> statuses,
            @NonNull Pageable pageable);

    @RestResource(exported = false)
    long deleteByActivatedAtBefore(@NonNull LocalDateTime cutoff);
}
