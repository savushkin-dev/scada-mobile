package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "device_catalog")
@Getter
@Setter
public class DeviceCatalogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "catalog_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = true)
    private DeviceTypeEntity type;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * Возвращает ID типа устройства для сериализации JSON (React Admin ожидает typeId).
     */
    public Long getTypeId() {
        return type != null ? type.getId() : null;
    }

    /**
     * Проверяет, полностью ли сконфигурировано устройство
     * (имеет тип и активно).
     */
    public boolean isFullyConfigured() {
        return type != null && active;
    }
}
