package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.AssignedUnitDTO;
import dev.savushkin.scada.mobile.backend.api.dto.UserProfileDTO;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtAuthenticationFilter;
import dev.savushkin.scada.mobile.backend.domain.model.AssignedUnit;
import dev.savushkin.scada.mobile.backend.domain.model.UserProfile;
import dev.savushkin.scada.mobile.backend.services.UserProfileService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${scada.api.base-path}")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/users/me")
    public ResponseEntity<UserProfileDTO> getProfile(
            @NonNull HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Long userId = resolveUserId(request);
        UserProfileService.ProfileSnapshot snapshot = profileService.getProfileSnapshot(userId);

        if (isNotModified(ifNoneMatch, snapshot.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(etagHeaders(snapshot.etag()))
                    .build();
        }

        UserProfile profile = snapshot.profile();
        UserProfileDTO body = new UserProfileDTO(
                profile.fullName(),
                profile.role(),
                profile.code(),
                mapAssignedUnits(profile.assignedUnits())
        );

        return ResponseEntity.ok()
                .headers(etagHeaders(snapshot.etag()))
                .body(body);
    }

    private List<AssignedUnitDTO> mapAssignedUnits(List<AssignedUnit> units) {
        return units.stream()
            .map(unit -> new AssignedUnitDTO(
                Long.toString(unit.unitId()),
                unit.unitName(),
                unit.printsrvInstanceId()
            ))
                .toList();
    }

    private Long resolveUserId(HttpServletRequest request) {
        String userIdRaw = JwtAuthenticationFilter.resolveUserId(request);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new IllegalArgumentException("Отсутствует аутентификация");
        }
        return parseLong(userIdRaw, "userId");
    }

    private Long parseLong(String raw, String field) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректный формат " + field);
        }
    }

    private static @NonNull HttpHeaders etagHeaders(String etag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"" + etag + "\"");
        return headers;
    }

    private static boolean isNotModified(@Nullable String ifNoneMatch, @NonNull String serverETag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String normalized = ifNoneMatch.trim();
        if ("*".equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2);
        }
        if (normalized.length() >= 2
                && normalized.charAt(0) == '"'
                && normalized.charAt(normalized.length() - 1) == '"') {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return serverETag.equals(normalized);
    }
}
