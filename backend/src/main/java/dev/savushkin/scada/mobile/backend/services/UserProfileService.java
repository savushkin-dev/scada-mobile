package dev.savushkin.scada.mobile.backend.services;

import dev.savushkin.scada.mobile.backend.application.ports.UserProfileRepository;
import dev.savushkin.scada.mobile.backend.domain.model.AssignedUnit;
import dev.savushkin.scada.mobile.backend.domain.model.UserProfile;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class UserProfileService {

    private final UserProfileRepository profileRepository;

    public UserProfileService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public @NonNull ProfileSnapshot getProfileSnapshot(long userId) {
        UserProfile baseProfile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Пользователь не найден"));

        if (!baseProfile.active()) {
            throw new ResponseStatusException(FORBIDDEN, "Пользователь заблокирован");
        }

        List<AssignedUnit> assignedUnits = profileRepository.findAssignedUnits(userId).stream()
                .sorted(Comparator.comparingLong(AssignedUnit::unitId))
                .toList();

        UserProfile profile = new UserProfile(
                baseProfile.id(),
                baseProfile.code(),
                baseProfile.fullName(),
                baseProfile.role(),
                baseProfile.active(),
                assignedUnits
        );

        String etag = computeProfileEtag(profile);
        return new ProfileSnapshot(profile, etag);
    }

    private String computeProfileEtag(UserProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(profile.id()).append('|')
                .append(profile.code()).append('|')
                .append(profile.fullName()).append('|')
                .append(profile.role()).append('|');

        for (AssignedUnit unit : profile.assignedUnits()) {
            sb.append(unit.unitId()).append(':').append(unit.unitName()).append(';');
        }

        return sha256(sb.toString());
    }

    private String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record ProfileSnapshot(
            UserProfile profile,
            String etag
    ) {
    }
}
