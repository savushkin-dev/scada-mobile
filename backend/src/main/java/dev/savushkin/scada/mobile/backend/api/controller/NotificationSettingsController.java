package dev.savushkin.scada.mobile.backend.api.controller;

import dev.savushkin.scada.mobile.backend.api.dto.NotificationSettingDTO;
import dev.savushkin.scada.mobile.backend.api.dto.NotificationSettingsUpdateDTO;
import dev.savushkin.scada.mobile.backend.config.jwt.JwtPrincipalUtil;
import dev.savushkin.scada.mobile.backend.domain.model.UnitNotificationPreference;
import dev.savushkin.scada.mobile.backend.services.NotificationSettingsService;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${scada.api.base-path}")
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;

    public NotificationSettingsController(NotificationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/notifications/settings")
    public ResponseEntity<List<NotificationSettingDTO>> getSettings(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("Отсутствует аутентификация");
        }
        NotificationSettingsService.SettingsSnapshot snapshot = settingsService.getSettingsSnapshot(userId);

        if (isNotModified(ifNoneMatch, snapshot.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(etagHeaders(snapshot.etag()))
                    .build();
        }

        List<NotificationSettingDTO> body = snapshot.preferences().stream()
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok()
                .headers(etagHeaders(snapshot.etag()))
                .body(body);
    }

    @PutMapping("/notifications/settings")
    public ResponseEntity<Void> updateSettings(
            @Valid @RequestBody NotificationSettingsUpdateDTO payload
    ) {
        Long userId = JwtPrincipalUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("Отсутствует аутентификация");
        }
        Long unitId = parseLong(payload.unitId(), "unitId");

        settingsService.updateSettings(userId, unitId, payload.techEnabled(), payload.masterEnabled());
        return ResponseEntity.noContent().build();
    }

    private NotificationSettingDTO toDto(UnitNotificationPreference pref) {
        return new NotificationSettingDTO(
                Long.toString(pref.unitId()),
                pref.unitName(),
                pref.techEnabled(),
                pref.masterEnabled()
        );
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
