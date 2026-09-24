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
     * Per-unit переопределение имени устройства (NULL → device_catalog.name).
     */
    @Column(name = "display_name")
    private String displayName;

    /**
     * Per-unit метка группы («Поток 2», «Агрегация»); NULL → группа по правилам умолчания.
     */
    @Column(name = "group_label")
    private String groupLabel;

    /**
     * Порядок отображения внутри группы.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Показывать ли счётчики «Считано/Несчитано» устройству.
     */
    @Column(name = "show_counters", nullable = false)
    private boolean showCounters;

    /**
     * Scada-префикс устройства (Dev041, LineDev011); NULL → вычислять по ScadaKeyMapper.
     */
    @Column(name = "scada_prefix")
    private String scadaPrefix;

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

    public String getName() {
        return catalog != null ? catalog.getName() : null;
    }

    /**
     * Эффективное отображаемое имя: per-unit переопределение, иначе имя каталога.
     */
    public String getDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return catalog != null ? catalog.getName() : null;
    }

    /**
     * Сырое per-unit переопределение имени (NULL, если не задано).
     */
    public String getDisplayNameOverride() {
        return displayName;
    }

    public DeviceTypeEntity getType() {
        return catalog != null ? catalog.getType() : null;
    }

    public Long getTypeId() {
        return catalog != null ? catalog.getTypeId() : null;
    }
}
