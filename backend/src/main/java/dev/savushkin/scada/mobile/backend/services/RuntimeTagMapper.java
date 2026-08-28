package dev.savushkin.scada.mobile.backend.services;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Общие правила чтения runtime-тегов PrintSrv. */
public final class RuntimeTagMapper {

    private static final Pattern DEVICE_PREFIX = Pattern.compile("(?:CMS)?(Dev\\d+|LineDev\\d+)");
        private static final List<String> ERROR_SUFFIXES = List.of(
            "Connection", "Connect", "Fail", "Dublicate", "DiffEan", "Work", "Data", "Batch", "Error"
        );

    private RuntimeTagMapper() {
    }

    public static CounterResolution resolveCounters(
            Map<String, String> cameraProperties,
            Map<String, String> scadaProperties,
            @Nullable String scadaPrefix
    ) {
        String scadaRead = value(scadaProperties, scadaPrefix, "CounterGeneral");
        String scadaUnread = value(scadaProperties, scadaPrefix, "CounterMissing");
        String read = firstPresent(scadaRead, cameraProperties.get("Total"));
        String unread = firstPresent(scadaUnread, cameraProperties.get("Failed"));
        return new CounterResolution(
                read,
                unread,
                scadaRead != null ? "scada:" + scadaPrefix + "CounterGeneral" : "device:Total",
                scadaUnread != null ? "scada:" + scadaPrefix + "CounterMissing" : "device:Failed"
        );
    }

    public static Optional<ErrorTag> parseErrorKey(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        for (String suffix : ERROR_SUFFIXES) {
            if (!key.endsWith(suffix) || key.length() == suffix.length()) {
                continue;
            }
            String rawPrefix = key.substring(0, key.length() - suffix.length());
            Matcher matcher = DEVICE_PREFIX.matcher(rawPrefix);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            return Optional.of(new ErrorTag(matcher.group(1), "Connect".equals(suffix) ? "Connection" : suffix));
        }
        return Optional.empty();
    }

    public static boolean isPotentialDeviceKey(@Nullable String key) {
        return key != null && (key.startsWith("Dev")
                || key.startsWith("CMSDev")
                || key.startsWith("LineDev"));
    }

    public static boolean isActiveFlag(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)
                || "yes".equalsIgnoreCase(normalized)
                || "on".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)
                || "no".equalsIgnoreCase(normalized)
                || "off".equalsIgnoreCase(normalized)) {
            return false;
        }
        try {
            return Double.parseDouble(normalized.replace(',', '.')) != 0.0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static boolean hasActiveError(Map<String, String> properties, @Nullable String devicePrefix) {
        if (devicePrefix == null) {
            return false;
        }
        return properties.entrySet().stream()
                .anyMatch(entry -> parseErrorKey(entry.getKey())
                        .filter(tag -> devicePrefix.equals(tag.objectName()))
                        .filter(tag -> isActiveFlag(entry.getValue()))
                        .isPresent());
    }

    private static @Nullable String value(
            Map<String, String> properties,
            @Nullable String prefix,
            String suffix
    ) {
        return prefix == null ? null : nullIfBlank(properties.get(prefix + suffix));
    }

    private static @Nullable String firstPresent(@Nullable String primary, @Nullable String fallback) {
        String primaryValue = nullIfBlank(primary);
        return primaryValue != null ? primaryValue : nullIfBlank(fallback);
    }

    private static @Nullable String nullIfBlank(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CounterResolution(
            @Nullable String read,
            @Nullable String unread,
            String readSource,
            String unreadSource
    ) {
    }

    public record ErrorTag(String objectName, String suffix) {
    }
}