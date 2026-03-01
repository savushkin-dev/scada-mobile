package dev.savushkin.scada.mobile.backend.api.dto;

public record WorkshopsDTO_new(
        int id,
        String name,
        int totalUnits,
        int problemUnits
) {
}
