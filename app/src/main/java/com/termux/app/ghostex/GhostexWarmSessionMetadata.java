package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;

public final class GhostexWarmSessionMetadata {

    private static final String ATTACH_LABEL_PREFIX = "ghostex-android-attach:";

    private GhostexWarmSessionMetadata() {}

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-16:16:
    Warm attach terminals can outlive an Activity/controller recreation because
    TermuxService keeps terminal sessions alive. Tag Ghostex attach commands with
    stable machine/session metadata so a new controller can rehydrate the
    last-seven warm pool instead of opening duplicate SSH attach terminals.
    */
    @NonNull
    public static String buildAttachCommandLabel(@NonNull String machineId,
                                                 @NonNull String sessionId) {
        return ATTACH_LABEL_PREFIX + hex(machineId) + ":" + hex(sessionId);
    }

    @Nullable
    public static GhostexWarmSessionIds parseAttachCommandLabel(@Nullable String label) {
        if (label == null || !label.startsWith(ATTACH_LABEL_PREFIX)) return null;
        String payload = label.substring(ATTACH_LABEL_PREFIX.length());
        int separator = payload.indexOf(':');
        if (separator <= 0 || separator >= payload.length() - 1) return null;
        String machineId = unhex(payload.substring(0, separator));
        String sessionId = unhex(payload.substring(separator + 1));
        if (machineId == null || machineId.isEmpty() || sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return new GhostexWarmSessionIds(machineId, sessionId);
    }

    @NonNull
    private static String hex(@NonNull String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0x0f, 16));
            builder.append(Character.forDigit(b & 0x0f, 16));
        }
        return builder.toString();
    }

    @Nullable
    private static String unhex(@NonNull String value) {
        if ((value.length() % 2) != 0) return null;
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) return null;
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static final class GhostexWarmSessionIds {
        public final String machineId;
        public final String sessionId;

        private GhostexWarmSessionIds(@NonNull String machineId,
                                      @NonNull String sessionId) {
            this.machineId = machineId;
            this.sessionId = sessionId;
        }
    }

}
