package com.termux.app.ghostex;

import androidx.annotation.Nullable;

public final class GhostexConnectionRecovery {

    private GhostexConnectionRecovery() {}

    /*
    CDXC:AndroidConnectionRecovery 2026-05-17-13:33:
    Password recovery is not only a reconnect concern. Remote session actions,
    project actions, rename, and machine checks all run through SSH and should
    route missing/rejected credential failures to the same password prompt.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:04:
    Host-key mismatch recovery is machine-specific. When Check connection sees
    that error, prompt for the selected target's known_hosts reset instead of
    leaving users to find the Host key action manually.
    */
    public static boolean shouldPromptForPassword(@Nullable String errorMessage) {
        if (errorMessage == null) return false;
        return errorMessage.contains("SSH needs a key or password") ||
            errorMessage.contains("SSH rejected");
    }

    public static boolean shouldPromptForHostKeyReset(@Nullable String errorMessage) {
        if (errorMessage == null) return false;
        return errorMessage.contains("SSH host key verification failed");
    }

}
