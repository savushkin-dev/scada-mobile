package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_unit_assignments")
@Getter
@Setter
public class UserAssignmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitEntity unit;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Возвращает ID пользователя для сериализации JSON (React Admin ожидает userId).
     */
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    /**
     * Возвращает ID аппарата для сериализации JSON (React Admin ожидает unitId).
     */
    public Long getUnitId() {
        return unit != null ? unit.getId() : null;
    }
}
