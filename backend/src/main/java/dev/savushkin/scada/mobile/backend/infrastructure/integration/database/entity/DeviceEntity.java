package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "unit_devices")
@Getter
@Setter
public class DeviceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitEntity unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private DeviceCatalogEntity catalog;

    /**
     * Возвращает ID аппарата для сериализации JSON (React Admin ожидает unitId).
     */
    public Long getUnitId() {
        return unit != null ? unit.getId() : null;
    }

    /**
     * Возвращает ID каталога для сериализации JSON (React Admin ожидает catalogId).
     */
    public Long getCatalogId() {
        return catalog != null ? catalog.getId() : null;
    }

    // Делегирующие методы для обратной совместимости
    public String getCode() {
        return catalog != null ? catalog.getCode() : null;
    }

    public String getDisplayName() {
        return catalog != null ? catalog.getDisplayName() : null;
    }

    public DeviceTypeEntity getType() {
        return catalog != null ? catalog.getType() : null;
    }

    public Long getTypeId() {
        return catalog != null ? catalog.getTypeId() : null;
    }
}
