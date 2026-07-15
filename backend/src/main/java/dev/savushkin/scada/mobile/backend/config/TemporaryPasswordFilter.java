package dev.savushkin.scada.mobile.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Фильтр, запрещающий доступ к защищённым endpoint'ам пользователям,
 * вошедшим с временным паролем, до тех пор пока пароль не будет сменён.
 * <p>
 * Разрешены только endpoints аутентификации и смены пароля.
 */
@Component
public class TemporaryPasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1.0.0/auth/login",
            "/api/v1.0.0/auth/logout",
            "/api/v1.0.0/auth/refresh",
            "/api/v1.0.0/auth/change-password"
    );

    private final ObjectMapper objectMapper;

    public TemporaryPasswordFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!ALLOWED_PATHS.contains(path) && hasTemporaryPassword()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error", "temporary_password",
                    "message", "Необходимо сменить временный пароль"
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasTemporaryPassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Boolean temporary = jwt.getClaimAsBoolean("temporary_password");
            return temporary != null && temporary;
        }
        return false;
    }
}
