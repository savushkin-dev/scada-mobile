package dev.savushkin.scada.mobile.backend.api.dto;

/**
 * Ответ на выпуск machine-токена (СКАДА).
 * <p>
 * Значение {@code token} возвращается один раз — при выпуске; в реестре
 * ({@code GET /admin/machine-tokens}) хранятся только метаданные.
 *
 * @param token              подписанный machine-JWT.
 * @param jti                идентификатор токена (для отзыва).
 * @param unitId             числовой id аппарата.
 * @param printsrvInstanceId PrintSrv instance id аппарата (sub токена).
 * @param expiresAt          ISO-8601 время истечения (UTC).
 */
public record MachineTokenIssueResponseDTO(
        String token,
        String jti,
        Long unitId,
        String printsrvInstanceId,
        String expiresAt
) {
}
