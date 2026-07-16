package dev.savushkin.scada.mobile.backend.domain.auth;

import org.jspecify.annotations.NonNull;

import java.util.regex.Pattern;

/**
 * Доменная политика паролей.
 * <p>
 * Правила едины для сгенерированных временных паролей и для паролей,
 * которые сотрудник устанавливает самостоятельно:
 * <ul>
 *   <li>длина от 6 до 20 символов;</li>
 *   <li>только буквы (латиница/кириллица) и цифры — без пробелов, спецсимволов и символов из других алфавитов;</li>
 *   <li>обязательно присутствуют и буквы, и цифры.</li>
 * </ul>
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 6;
    private static final int MAX_LENGTH = 20;

    // \p{L} — любая буква (латиница, кириллица и т.д.), \p{N} — любая цифра
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("^[\\p{L}\\p{N}]+");
    private static final Pattern CONTAINS_LETTER = Pattern.compile(".*\\p{L}.*");
    private static final Pattern CONTAINS_DIGIT = Pattern.compile(".*\\p{N}.*");

    private PasswordPolicy() {
        // утилитный класс
    }

    /**
     * Проверяет пароль на соответствие политике.
     *
     * @param password проверяемый пароль
     * @throws InvalidPasswordException если пароль не соответствует правилам
     */
    public static void validate(@NonNull String password) {
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new InvalidPasswordException(
                    "Пароль должен содержать от " + MIN_LENGTH + " до " + MAX_LENGTH + " символов"
            );
        }
        if (!ALLOWED_CHARACTERS.matcher(password).matches()) {
            throw new InvalidPasswordException(
                    "Пароль может содержать только буквы и цифры"
            );
        }
        if (!CONTAINS_LETTER.matcher(password).matches()) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну букву");
        }
        if (!CONTAINS_DIGIT.matcher(password).matches()) {
            throw new InvalidPasswordException("Пароль должен содержать хотя бы одну цифру");
        }
    }

    /**
     * Проверяет, что два введённых пароля совпадают.
     *
     * @param password     первое значение
     * @param confirmation подтверждение
     * @throws InvalidPasswordException если значения не совпадают
     */
    public static void validateMatch(@NonNull String password, @NonNull String confirmation) {
        if (!password.equals(confirmation)) {
            throw new InvalidPasswordException("Пароли не совпадают");
        }
    }

    public static final class InvalidPasswordException extends RuntimeException {
        public InvalidPasswordException(String message) {
            super(message);
        }
    }
}
