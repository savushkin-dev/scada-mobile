package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    @Pattern(regexp = "^\\d{5}$", message = "Табельный номер должен состоять из 5 цифр")
    private String code;

    @Column(name = "password", nullable = false, length = 60)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "password_temporary", nullable = false)
    private boolean passwordTemporary = false;

    /** Момент последней активности сотрудника (логин, refresh токенов, смена пароля). */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @OneToMany(mappedBy = "user")
    @JsonIgnore
    private Set<UserAssignmentEntity> assignments;

    /**
     * Возвращает ID роли для сериализации JSON (React Admin ожидает roleId).
     */
    public Long getRoleId() {
        return role != null ? role.getId() : null;
    }
}
