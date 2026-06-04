package dev.savushkin.scada.mobile.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.api.dto.AuthErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom {@link AuthenticationEntryPoint} для возврата консистентного
 * {@link AuthErrorResponseDTO} при 401 (Unauthorized) от Spring Security.
 * <p>
 * Заменяет дефолтный {@code BearerTokenAuthenticationEntryPoint}, который возвращает
 * неструктурированный JSON вроде {@code {"error":"invalid_token"}}.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull AuthenticationException authException
    ) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        AuthErrorResponseDTO error = AuthErrorResponseDTO.error(
                "Сессия истекла или недействительна. Войдите заново."
        );

        objectMapper.writeValue(response.getWriter(), error);
    }
}
