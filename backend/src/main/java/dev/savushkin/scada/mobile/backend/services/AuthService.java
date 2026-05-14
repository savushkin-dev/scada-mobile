package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.UserAuthRepository;
import dev.savushkin.scada.mobile.backend.domain.model.AuthUser;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAuthRepository userAuthRepository;

    public AuthService(UserAuthRepository userAuthRepository) {
        this.userAuthRepository = userAuthRepository;
    }

    public @NonNull AuthUser authenticate(@NonNull String workerCode, @NonNull String password) {
        String code = normalize(workerCode);
        String pass = normalize(password);
        if (code.isEmpty() || pass.isEmpty()) {
            throw new InvalidCredentialsException(code);
        }

        AuthUser user = userAuthRepository.findByCredentials(code, pass)
                .orElseThrow(() -> new InvalidCredentialsException(code));

        if (!user.active()) {
            throw new UserInactiveException(user.id(), user.code());
        }

        return user;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static class InvalidCredentialsException extends RuntimeException {
        private final String code;

        public InvalidCredentialsException(String code) {
            super("Invalid credentials");
            this.code = code == null ? "" : code;
        }

        public String code() {
            return code;
        }
    }

    public static class UserInactiveException extends RuntimeException {
        private final long userId;
        private final String code;

        public UserInactiveException(long userId, String code) {
            super("User inactive");
            this.userId = userId;
            this.code = code == null ? "" : code;
        }

        public long userId() {
            return userId;
        }

        public String code() {
            return code;
        }
    }
}
