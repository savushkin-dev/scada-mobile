package dev.savushkin.scada.mobile.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtTokenProvider;
import dev.savushkin.scada.mobile.backend.infrastructure.integration.database.repository.UnitJpaRepository;
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

/**
 * Фильтр отзыва machine-токенов (автоматы / СКАДА).
 * <p>
 * Выполняется после {@code BearerTokenAuthenticationFilter}: если запрос
 * аутентифицирован токеном с {@code subject_type = "machine"}, проверяет,
 * что {@code sub} и {@code unitId} токена соответствуют активной записи
 * автомата в {@code units}. Иначе — 401.
 * <p>
 * Пользовательские токены ({@code subject_type = "user}" или без claim)
 * проходят без дополнительной проверки.
 */
@Component
public class MachineTokenRevocationFilter extends OncePerRequestFilter {

    private final UnitJpaRepository unitRepository;
    private final ObjectMapper objectMapper;

    public MachineTokenRevocationFilter(UnitJpaRepository unitRepository,
                                        ObjectMapper objectMapper) {
        this.unitRepository = unitRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isRevokedMachineToken()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error", "machine_token_revoked",
                    "message", "Machine-токен недействителен или автомат удалён"
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRevokedMachineToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            return false;
        }
        if (!JwtTokenProvider.SUBJECT_TYPE_MACHINE.equals(
                jwt.getClaimAsString(JwtTokenProvider.SUBJECT_TYPE_CLAIM))) {
            return false;
        }
        Object rawUnitId = jwt.getClaim(JwtTokenProvider.MACHINE_UNIT_ID_CLAIM);
        Long unitId = rawUnitId instanceof Number number ? number.longValue() : null;
        String printSrvId = jwt.getSubject();
        return unitId == null || printSrvId == null
            || unitRepository.findByPrintsrvInstanceId(printSrvId)
            .filter(unit -> unit.isActive() && unit.getId().equals(unitId))
            .isEmpty();
    }
}
