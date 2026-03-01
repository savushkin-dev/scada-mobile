package dev.savushkin.scada.mobile.backend.api.dto;

import java.time.Duration;

public record UnitsDTO_new(
        int id,
        int workshopId,
        String unit,
        String event,
        Duration timer
) {
}
