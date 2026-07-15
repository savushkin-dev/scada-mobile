package dev.savushkin.scada.mobile.backend.domain.auth;

import org.jspecify.annotations.NonNull;

import java.security.SecureRandom;

/**
 * Генератор учётных данных сотрудника.
 * <p>
 * Генерирует код сотрудника (логин) и временный пароль, удобные для ручного ввода
 * на экране телефона. Из набора символов исключены похожие друг на друга
 * (O, I, L, 0, 1), а также исключаются последовательности из трёх и более
 * одинаковых символов подряд.
 */
public final class EmployeeCredentialsGenerator {

    private static final String LETTERS = "ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;

    private static final int CODE_LENGTH = 8;
    private static final int PASSWORD_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private EmployeeCredentialsGenerator() {
        // утилитный класс
    }

    /**
     * Генерирует код сотрудника заданной длины.
     * <p>
     * Код гарантированно содержит как минимум одну букву и одну цифру.
     *
     * @return уникальный код сотрудника
     */
    public static @NonNull String generateCode() {
        return generateMixed(CODE_LENGTH);
    }

    /**
     * Генерирует временный пароль заданной длины.
     * <p>
     * Пароль гарантированно содержит как минимум одну букву и одну цифру
     * и проходит валидацию {@link PasswordPolicy}.
     *
     * @return временный пароль
     */
    public static @NonNull String generateTemporaryPassword() {
        String password;
        do {
            password = generateMixed(PASSWORD_LENGTH);
        } while (!isValidPassword(password));
        return password;
    }

    private static @NonNull String generateMixed(int length) {
        StringBuilder builder = new StringBuilder(length);

        // гарантируем минимум одну букву и одну цифру
        builder.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        builder.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));

        for (int i = 2; i < length; i++) {
            char next = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
            // исключаем троекратное повторение одного символа подряд
            if (i >= 2 && builder.charAt(i - 1) == next && builder.charAt(i - 2) == next) {
                i--;
                continue;
            }
            builder.append(next);
        }

        // перемешиваем, чтобы позиции фиксированных буквы/цифры не были предсказуемы
        char[] chars = builder.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static boolean isValidPassword(String password) {
        try {
            PasswordPolicy.validate(password);
            return true;
        } catch (PasswordPolicy.InvalidPasswordException e) {
            return false;
        }
    }
}
