package dev.savushkin.scada.mobile.backend.domain.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "abc123",
            "ABC123",
            "Password1",
            "кириллица9",
            "a1b2c3d4e5f6g7h8i9j0",
            "1234567890abcdef"
    })
    void validPasswords(String password) {
        assertThatNoException().isThrownBy(() -> PasswordPolicy.validate(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",
            "12345",
            "abcdefghij",
            "123456",
            "pass!word1",
            "password 1",
            "tooLongPassword1234567890",
            ""
    })
    void invalidPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(PasswordPolicy.InvalidPasswordException.class);
    }

    @Test
    void matchingPasswords() {
        assertThatNoException().isThrownBy(() -> PasswordPolicy.validateMatch("abc123", "abc123"));
    }

    @Test
    void nonMatchingPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validateMatch("abc123", "abc124"))
                .isInstanceOf(PasswordPolicy.InvalidPasswordException.class)
                .hasMessageContaining("не совпадают");
    }
}
