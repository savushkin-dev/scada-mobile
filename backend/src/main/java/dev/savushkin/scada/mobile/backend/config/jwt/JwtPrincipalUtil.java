package dev.savushkin.scada.mobile.backend.config.jwt;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Утилита для извлечения данных из аутентифицированного JWT principal.
 * <p>
 * Используется в контроллерах, которым нужен идентификатор текущего пользователя
 * или тип субъекта (работник / автомат).
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
     *         либо субъект токена — не пользователь (machine-JWT)
     */
    public static @Nullable Long getCurrentUserId() {
        Jwt jwt = getCurrentJwt();
        if (jwt == null) {
            return null;
        }
        String subject = jwt.getSubject();
        if (subject != null && !subject.isBlank()) {
            try {
                return Long.parseLong(subject.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Возвращает тип субъекта текущего токена (claim {@code subject_type}).
     * <p>
     * Токены без claim (выданные до появления типизации) трактуются как
     * {@link JwtTokenProvider#SUBJECT_TYPE_USER} — обратная совместимость.
     *
     * @return {@code "user"} или {@code "machine"}; {@code null} если нет аутентификации
     */
    public static @Nullable String getCurrentSubjectType() {
        Jwt jwt = getCurrentJwt();
        if (jwt == null) {
            return null;
        }
        String subjectType = jwt.getClaimAsString(JwtTokenProvider.SUBJECT_TYPE_CLAIM);
        return subjectType != null ? subjectType : JwtTokenProvider.SUBJECT_TYPE_USER;
    }

    /**
     * {@code true}, если текущий запрос аутентифицирован machine-JWT (СКАДА).
     */
    public static boolean isMachineSubject() {
        return JwtTokenProvider.SUBJECT_TYPE_MACHINE.equals(getCurrentSubjectType());
    }

    /**
     * Возвращает текущий JWT principal.
     *
     * @return JWT или {@code null}, если запрос не аутентифицирован
     */
    public static @Nullable Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }
}
