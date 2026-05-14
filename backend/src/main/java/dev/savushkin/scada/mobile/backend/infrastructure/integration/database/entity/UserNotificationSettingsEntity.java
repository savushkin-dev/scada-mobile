package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_notification_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_user_notification_settings_user_unit",
                columnNames = {"user_id", "unit_id"}
        ),
        indexes = {
                @Index(name = "idx_user_notification_settings_user_id", columnList = "user_id"),
                @Index(name = "idx_user_notification_settings_unit_id", columnList = "unit_id")
        }
)
@Getter
@Setter
public class UserNotificationSettingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitEntity unit;

        @Column(name = "incident_notifications_enabled", nullable = false)
        private boolean incidentNotificationsEnabled = true;

        @Column(name = "android_call_notifications_enabled", nullable = false)
        private boolean androidCallNotificationsEnabled = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
