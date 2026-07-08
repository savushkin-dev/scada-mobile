package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationSeverity;
import dev.savushkin.scada.mobile.backend.domain.model.AdminNotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA-сущность системного уведомления администратора.
 */
@Entity
@Table(name = "admin_notifications")
@Getter
@Setter
public class AdminNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AdminNotificationType type;

    @Column(name = "severity", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AdminNotificationSeverity severity;

    @Column(name = "instance_id", nullable = false)
    private String instanceId;

    @Column(name = "device_code")
    private String deviceCode;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
