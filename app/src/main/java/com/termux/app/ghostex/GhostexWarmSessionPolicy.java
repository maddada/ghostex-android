package com.termux.app.ghostex;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GhostexWarmSessionPolicy {

    private GhostexWarmSessionPolicy() {}

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-17:55:
    Kill and sleep invalidate a local warm attach surface because the remote ZMX
    lifecycle changed. Wake/focus/rename/refresh should preserve the warm
    terminal so quick switching remains fast when the remote session stays live.

    CDXC:AndroidRemoteSessions 2026-05-17-14:30:
    The warm attach cache is access-order so "last seven clicked" stays true.
    Dead-session cleanup must inspect entry values without calling `get()`,
    because `get()` mutates access order and can destabilize iteration.

    CDXC:AndroidRemoteSessions 2026-05-17-16:22:
    Activity rehydration can discover more than seven still-running attach
    terminals from TermuxService. Eviction should preserve the current visible
    terminal even if service iteration makes it look oldest, then remove the
    oldest non-current entries until the warm pool is back at seven.
    */
    public static boolean shouldCloseWarmSessionForAction(@NonNull String action) {
        return "kill".equals(action) || "sleep".equals(action);
    }

    @NonNull
    public static <T> List<String> deadWarmSessionKeys(@NonNull LinkedHashMap<String, T> sessions,
                                                       @NonNull WarmSessionState<T> state) {
        ArrayList<String> deadKeys = new ArrayList<>();
        for (Map.Entry<String, T> entry : sessions.entrySet()) {
            if (!state.isRunning(entry.getValue())) deadKeys.add(entry.getKey());
        }
        return deadKeys;
    }

    @NonNull
    public static List<String> overflowWarmSessionKeys(@NonNull LinkedHashMap<String, ?> sessions,
                                                       int limit,
                                                       @NonNull String protectedKey) {
        ArrayList<String> overflowKeys = new ArrayList<>();
        int remaining = sessions.size();
        if (remaining <= limit) return overflowKeys;
        for (String key : sessions.keySet()) {
            if (remaining <= limit) break;
            if (protectedKey.equals(key)) continue;
            overflowKeys.add(key);
            remaining--;
        }
        return overflowKeys;
    }

    public interface WarmSessionState<T> {
        boolean isRunning(T session);
    }

}
