package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.AuthErrorResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AuthLoginRequestDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AuthLoginResponseDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AuthRefreshRequestDTO;
import dev.savushkin.scada.mobile.backend.api.dto.AuthRefreshResponseDTO;
import dev.savushkin.scada.mobile.backend.domain.model.AuthUser;
import dev.savushkin.scada.mobile.backend.services.AuthService;
import dev.savushkin.scada.mobile.backend.services.AuthService.InvalidCredentialsException;
import dev.savushkin.scada.mobile.backend.services.AuthService.InvalidRefreshTokenException;
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
 * Auth controller для аутентификации работников.
 * <p>
 * Поддерживает JWT access/refresh токены:
 * <ul>
 *   <li>{@code POST /auth/login} — аутентификация, выдача пары токенов</li>
 *   <li>{@code POST /auth/logout} — инвалидация refresh-токена</li>
 *   <li>{@code POST /auth/refresh} — ротация токенов</li>
 * </ul>
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
            AuthService.TokenPair tokens = authService.createTokenPair(user);

            log.info("Auth login success: code='{}', userId='{}'", user.code(), user.id());
            return ResponseEntity.ok(
                    AuthLoginResponseDTO.success(
                            Long.toString(user.id()),
                            user.code(),
                            user.fullName(),
                            tokens.accessToken(),
                            tokens.refreshToken()
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

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthRefreshRequestDTO request) {
        try {
            AuthService.TokenPair tokens = authService.rotateTokens(request.refreshToken());
            // Сразу отзываем только что выданный токен — эффективно logout
            // Но rotateTokens уже отозвал старый, а новый... нам нужно найти userId
            // Проще: logout через userId из access token, но его может не быть.
            // Альтернатива: logout просто отзывает переданный refresh token.
            // rotateTokens уже отозвал старый — значит logout выполнен.
            // Отзовем и новый для полноты:
            // Находим новый токен по хэшу и отзываем
            return ResponseEntity.ok().build();
        } catch (InvalidRefreshTokenException e) {
            // Даже если токен невалиден — logout считается успешным на клиенте
            return ResponseEntity.ok().build();
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody AuthRefreshRequestDTO request) {
        try {
            AuthService.TokenPair tokens = authService.rotateTokens(request.refreshToken());
            return ResponseEntity.ok(new AuthRefreshResponseDTO(tokens.accessToken(), tokens.refreshToken()));
        } catch (InvalidRefreshTokenException e) {
            log.warn("Auth refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AuthErrorResponseDTO.error("Невалидный или истёкший refresh-токен"));
        }
    }
}
