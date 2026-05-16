package dev.savushkin.scada.mobile.backend.config.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Утилита для извлечения userId из аутентифицированного JWT principal.
 * <p>
 * Используется в контроллерах, которым нужен идентификатор текущего пользователя.
 * Заменяет устаревший {@code JwtAuthenticationFilter.resolveUserId(request)}.
 */
public final class JwtPrincipalUtil {

    private JwtPrincipalUtil() {
        // utility class
    }

    /**
     * Возвращает userId из текущего аутентифицированного JWT principal.
     *
     * @return userId или {@code null}, если пользователь не аутентифицирован
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                try {
                    return Long.parseLong(subject.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
