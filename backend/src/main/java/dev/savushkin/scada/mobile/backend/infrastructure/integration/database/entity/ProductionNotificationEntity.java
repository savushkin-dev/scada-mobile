package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import dev.savushkin.scada.mobile.backend.domain.model.NotificationCreatorType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA-сущность перманентного состояния производственного уведомления
 * («последняя партия») по аппарату.
 * <p>
 * Одна строка на аппарат ({@code unit_id} — unique): запись создаётся при первой
 * активации и далее обновляется на месте при каждом toggle.
 */
@Entity
@Table(name = "production_notifications")
@Getter
@Setter
public class ProductionNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "creator_type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private NotificationCreatorType creatorType;

    @Column(name = "creator_id", nullable = false)
    private String creatorId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;
}
