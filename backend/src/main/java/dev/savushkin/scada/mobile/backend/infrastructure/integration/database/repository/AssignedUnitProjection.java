package dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository;

public interface AssignedUnitProjection {
    Long getUnitId();
    String getUnitName();
    String getPrintsrvInstanceId();
}
