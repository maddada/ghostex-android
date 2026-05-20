package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class GhostexSessionCardFormatter {

    private static final long SECOND_MS = 1_000L;
    private static final long MINUTE_MS = 60L * SECOND_MS;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;

    private GhostexSessionCardFormatter() {}

    /*
    CDXC:AndroidSidebar 2026-05-17-12:45:
    Android session cards should mirror the macOS sidebar's scan order and
    recency cues, not just list title/provider text. Keep formatting and
    ordering in a small helper so the native row adapter can stay UI-focused
    while unit tests protect activity-priority and Last Active behavior.

    CDXC:AndroidSidebar 2026-05-17-12:59:
    Session details use the same Last Active formatter as compact drawer rows
    so the Android long-press Details sheet exposes useful remote debugging
    context without drifting from the macOS sidebar's recency wording.

    CDXC:AndroidSidebar 2026-05-17-14:44:
    Last Active is release-critical sidebar metadata. Parse standard ISO
    timestamps with either UTC `Z` or explicit offsets so Android keeps recency
    sorting and labels even if the Mac bridge emits local-offset ISO strings.

    CDXC:AndroidSidebar 2026-05-19-10:15:
    Drawer session rows no longer render buildMeta output. Keep this formatter
    for sidebar sort order and the long-press Details sheet instead of duplicating
    recency formatting in the adapter.
    */
    public static String buildMeta(@NonNull GhostexRemoteSession session, long nowMs) {
        StringBuilder builder = new StringBuilder();
        appendPart(builder, session.projectName.isEmpty() ? "Remote project" : session.projectName);
        String lastActive = formatLastActive(session.lastInteractionAt, nowMs);
        if (!lastActive.isEmpty()) appendPart(builder, lastActive);
        appendPart(builder, session.providerSessionName.isEmpty() ? "zmx" : session.providerSessionName);
        appendPart(builder, session.agent.isEmpty() ? "terminal" : session.agent);
        return builder.toString();
    }

    public static String formatLastActive(@Nullable String timestamp, long nowMs) {
        long value = parseIsoTimestamp(timestamp);
        if (value <= 0L) return "";
        long ageMs = Math.max(0L, nowMs - value);
        if (ageMs < MINUTE_MS) return Math.max(1L, Math.round((double) ageMs / SECOND_MS)) + "s ago";
        if (ageMs < HOUR_MS) return Math.round((double) ageMs / MINUTE_MS) + "m ago";
        if (ageMs < 48L * HOUR_MS) return Math.round((double) ageMs / HOUR_MS) + "h ago";
        return Math.round((double) ageMs / DAY_MS) + "d ago";
    }

    public static String formatLastActiveDetail(@Nullable String timestamp, long nowMs) {
        String value = formatLastActive(timestamp, nowMs);
        return value.isEmpty() ? "Unknown" : value;
    }

    public static int compareForSidebarOrder(@NonNull GhostexRemoteSession left,
                                             @NonNull GhostexRemoteSession right) {
        int priorityDelta = activityPriority(right) - activityPriority(left);
        if (priorityDelta != 0) return priorityDelta;
        long timeDelta = parseIsoTimestamp(right.lastInteractionAt) - parseIsoTimestamp(left.lastInteractionAt);
        if (timeDelta > 0L) return 1;
        if (timeDelta < 0L) return -1;
        return 0;
    }

    private static int activityPriority(@NonNull GhostexRemoteSession session) {
        String status = session.displayStatus();
        if ("attention".equals(status)) return 2;
        if ("working".equals(status)) return 1;
        return 0;
    }

    private static void appendPart(@NonNull StringBuilder builder, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append(" · ");
        builder.append(value.trim());
    }

    private static long parseIsoTimestamp(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        String normalized = normalizeIsoTimestamp(value.trim());
        String pattern = normalized.endsWith("Z")
            ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            : "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
        try {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = format.parse(normalized);
            return parsed == null ? 0L : parsed.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String normalizeIsoTimestamp(@NonNull String value) {
        int zoneStart = timezoneStart(value);
        String body = zoneStart >= 0 ? value.substring(0, zoneStart) : value;
        String zone = normalizeTimezoneOffset(zoneStart >= 0 ? value.substring(zoneStart) : "Z");
        int dotIndex = body.lastIndexOf('.');
        if (dotIndex < 0) return body + ".000" + zone;
        String fraction = body.substring(dotIndex + 1);
        if (fraction.length() >= 3) return body.substring(0, dotIndex + 1) + fraction.substring(0, 3) + zone;
        if (fraction.length() == 2) return body + "0" + zone;
        if (fraction.length() == 1) return body + "00" + zone;
        return body + "000" + zone;
    }

    private static int timezoneStart(@NonNull String value) {
        if (value.endsWith("Z")) return value.length() - 1;
        int timeStart = value.indexOf('T');
        if (timeStart < 0) return -1;
        int plus = value.indexOf('+', timeStart);
        int minus = value.indexOf('-', timeStart);
        if (plus < 0) return minus;
        if (minus < 0) return plus;
        return Math.min(plus, minus);
    }

    private static String normalizeTimezoneOffset(@NonNull String zone) {
        if ("Z".equals(zone)) return zone;
        if (zone.length() == 6 && zone.charAt(3) == ':') {
            return zone.substring(0, 3) + zone.substring(4);
        }
        return zone;
    }

}
