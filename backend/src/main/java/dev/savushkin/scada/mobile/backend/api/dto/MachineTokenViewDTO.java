package dev.savushkin.scada.mobile.backend.api.dto;

import org.jspecify.annotations.Nullable;

/**
 * Элемент реестра machine-токенов (без значения самого токена).
 *
 * @param jti                идентификатор токена.
 * @param unitId             числовой id аппарата.
 * @param printsrvInstanceId PrintSrv instance id аппарата.
 * @param issuedBy           id администратора, выдавшего токен.
 * @param issuedAt           ISO-8601 время выпуска (UTC).
 * @param expiresAt          ISO-8601 время истечения (UTC).
 * @param revokedAt          ISO-8601 время отзыва; {@code null} — токен не отозван.
 * @param active             {@code true} — токен действителен (не отозван и не истёк).
 */
public record MachineTokenViewDTO(
        String jti,
        Long unitId,
        @Nullable String printsrvInstanceId,
        @Nullable Long issuedBy,
        String issuedAt,
        String expiresAt,
        @Nullable String revokedAt,
        boolean active
) {
}
