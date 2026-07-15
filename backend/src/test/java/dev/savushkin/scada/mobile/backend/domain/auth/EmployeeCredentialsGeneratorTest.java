package dev.savushkin.scada.mobile.backend.domain.auth;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class EmployeeCredentialsGeneratorTest {

    @RepeatedTest(20)
    void generatedCodeHasExpectedFormat() {
        String code = EmployeeCredentialsGenerator.generateCode();

        assertThat(code).hasSize(8);
        assertThat(code).containsPattern("^[A-Z0-9]+$");
        assertThat(code).containsPattern(".*[A-Z].*");
        assertThat(code).containsPattern(".*[0-9].*");
        assertThat(code).doesNotContainPattern("(.)\\1{2,}");
        assertThat(code).doesNotContain("O", "I", "L", "0", "1");
    }

    @RepeatedTest(20)
    void generatedPasswordIsValid() {
        String password = EmployeeCredentialsGenerator.generateTemporaryPassword();

        assertThat(password).hasSize(8);
        assertThatNoException().isThrownBy(() -> PasswordPolicy.validate(password));
    }

    @Test
    void codesAreUniqueInReasonableSample() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(EmployeeCredentialsGenerator.generateCode());
        }
        assertThat(codes).hasSize(1000);
    }
}
