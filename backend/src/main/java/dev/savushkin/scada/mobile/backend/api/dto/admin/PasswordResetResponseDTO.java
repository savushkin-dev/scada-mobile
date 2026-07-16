package dev.savushkin.scada.mobile.backend.api.dto.admin;

import org.jspecify.annotations.NonNull;

/**
 * Ответ на сброс пароля сотруднику.
 * <p>
 * Содержит код сотрудника, ФИО и новый временный пароль, который показывается
 * администратору только один раз.
 */
public record PasswordResetResponseDTO(
        @NonNull String code,
        @NonNull String fullName,
        @NonNull String generatedPassword
) {
}
