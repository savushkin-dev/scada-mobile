package dev.savushkin.scada.mobile.backend.api.dto.admin;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Ответ на создание сотрудника.
 * <p>
 * Содержит данные созданного сотрудника и сгенерированный временный пароль,
 * который показывается администратору только один раз.
 */
public record UserCreateResponseDTO(
        long id,
        @NonNull String code,
        @NonNull String fullName,
        long roleId,
        boolean active,
        List<Long> unitIds,
        @NonNull String generatedPassword
) {
}
