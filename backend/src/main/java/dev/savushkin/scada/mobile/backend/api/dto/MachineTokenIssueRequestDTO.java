package dev.savushkin.scada.mobile.backend.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Запрос на выпуск machine-токена (СКАДА) для аппарата.
 *
 * @param unitId  числовой id аппарата, которому выдаётся токен.
 * @param ttlDays срок жизни токена в днях (1..3650); {@code null} — дефолт из
 *                конфигурации {@code jwt.machine-token-expiration-days}.
 */
public record MachineTokenIssueRequestDTO(
        @NotNull Long unitId,
        @Nullable @Min(1) @Max(3650) Integer ttlDays
) {
}
