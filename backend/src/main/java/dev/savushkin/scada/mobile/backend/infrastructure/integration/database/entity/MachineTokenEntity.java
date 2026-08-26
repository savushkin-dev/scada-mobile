package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * JPA-сущность реестра machine-токенов (автоматы / СКАДА).
 * <p>
 * Сам токен не хранится — только его {@code jti} и метаданные.
 * Отзыв выполняется установкой {@code revoked_at}; проверка — при каждом
 * запросе с machine-JWT (HTTP-фильтр и WS-interceptor).
 */
@Entity
@Table(name = "machine_tokens")
@Getter
@Setter
public class MachineTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long id;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "jti", nullable = false, length = 64)
    private String jti;

    /** Администратор, выдавший токен (null для исторических записей). */
    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
}
