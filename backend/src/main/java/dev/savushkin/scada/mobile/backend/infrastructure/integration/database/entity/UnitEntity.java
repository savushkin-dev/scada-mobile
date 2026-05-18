package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "units")
@Getter
@Setter
public class UnitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id", nullable = false)
    private WorkshopEntity workshop;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "printsrv_instance_id")
    private String printsrvInstanceId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "unit")
    @JsonIgnore
    private Set<DeviceEntity> devices;

    @OneToMany(mappedBy = "unit")
    @JsonIgnore
    private Set<UserAssignmentEntity> assignments;

    /**
     * Возвращает ID цеха для сериализации JSON (React Admin ожидает workshopId).
     */
    public Long getWorkshopId() {
        return workshop != null ? workshop.getId() : null;
    }
}
