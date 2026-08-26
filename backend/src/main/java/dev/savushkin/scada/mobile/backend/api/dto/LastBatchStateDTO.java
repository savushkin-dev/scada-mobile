package dev.savushkin.scada.mobile.backend.api.dto;

import org.jspecify.annotations.Nullable;

/**
 * DTO текущего состояния «последняя партия» по аппарату
 * (GET /api/v1.0.0/line/{unitId}/last-batch).
 * <p>
 * Единый источник истины для фронтенда и СКАДА: состояние хранится
 * перманентно на сервере (таблица {@code production_notifications}).
 *
 * @param unitId             числовой id аппарата (строкой).
 * @param printsrvInstanceId PrintSrv instance id аппарата.
 * @param active             {@code true} — флаг «последняя партия» установлен.
 * @param creatorType        тип создателя: {@code "USER"} или {@code "MACHINE"}
 *                           ({@code null}, если флаг не установлен).
 * @param creatorId          идентификатор создателя: для {@code USER} — id работника,
 *                           для {@code MACHINE} — PrintSrv instance id автомата.
 * @param activatedAt        ISO-8601 время установки флага (UTC); {@code null}, если не активен.
 */
public record LastBatchStateDTO(
        String unitId,
        String printsrvInstanceId,
        boolean active,
        @Nullable String creatorType,
        @Nullable String creatorId,
        @Nullable String activatedAt
) {
    public static LastBatchStateDTO active(String unitId, String printsrvInstanceId,
                                           String creatorType, String creatorId, String activatedAt) {
        return new LastBatchStateDTO(unitId, printsrvInstanceId, true, creatorType, creatorId, activatedAt);
    }

    public static LastBatchStateDTO inactive(String unitId, String printsrvInstanceId) {
        return new LastBatchStateDTO(unitId, printsrvInstanceId, false, null, null, null);
    }
}
