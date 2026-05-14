package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.AuthErrorResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AuthLoginRequestDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AuthLoginResponseDTO;
import dev.savushkin.scada.mobile.backend.domain.model.AuthUser;
import dev.savushkin.scada.mobile.backend.services.AuthService;
import dev.savushkin.scada.mobile.backend.services.AuthService.InvalidCredentialsException;
import dev.savushkin.scada.mobile.backend.services.AuthService.UserInactiveException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth controller for worker login.
 */
@RestController
@RequestMapping("${scada.api.base-path}")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthLoginRequestDTO request) {
        try {
            AuthUser user = authService.authenticate(request.workerCode(), request.password());
            log.info("Auth login success: code='{}', userId='{}'", user.code(), user.id());
            return ResponseEntity.ok(
                    AuthLoginResponseDTO.success(
                            Long.toString(user.id()),
                            user.code(),
                            user.fullName()
                    )
            );
        } catch (InvalidCredentialsException ex) {
            if (!ex.code().isBlank()) {
                log.warn("Auth login failed: invalid credentials for code='{}'", ex.code());
            } else {
                log.warn("Auth login failed: invalid credentials");
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthErrorResponseDTO.error(
                            "Пользователь не найден, проверьте правильность введенных данных"
                    ));
        } catch (UserInactiveException ex) {
            log.warn("Auth login blocked: inactive userId='{}' code='{}'", ex.userId(), ex.code());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AuthErrorResponseDTO.error("Пользователь заблокирован"));
        }
    }
}
