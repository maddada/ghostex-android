package com.termux.app.ghostex;

import androidx.annotation.NonNull;

public final class GhostexWarmSessionKey {

    private GhostexWarmSessionKey() {}

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-17:34:
    Warm terminal surfaces must be scoped by saved SSH machine and remote
    Ghostex session id. Users can switch between multiple Macs, and two
    machines may expose the same sidebar session id; the LRU cache must never
    reuse a terminal attached to a different machine.

    CDXC:AndroidSidebar 2026-05-17-20:27:
    The drawer's active-session highlight must use the same machine-scoped key
    as warm terminal reuse. A session id that appears on two saved machines
    should not make both Android sidebars look active.
    */
    public static String forSession(@NonNull GhostexMachine machine,
                                    @NonNull GhostexRemoteSession session) {
        return forIds(machine.id, session.sessionId);
    }

    public static String forIds(@NonNull String machineId, @NonNull String sessionId) {
        return machineId + "::" + sessionId;
    }

    public static boolean belongsToMachine(@NonNull String key, @NonNull String machineId) {
        return key.startsWith(machineId + "::");
    }

}
