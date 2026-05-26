package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

public final class GhostexZmxViewportRefresh {

    private static final String REFRESH_SEQUENCE = "\u001B]1337;ZMX_REFRESH\u0007";

    private GhostexZmxViewportRefresh() {}

    /*
    CDXC:AndroidRemoteAttach 2026-05-26-20:32:
    Selecting a ZMX-backed session from the Android sidebar should mirror the
    macOS focus/surface behavior by sending zmx's private refresh OSC once
    after the terminal becomes current. zmx consumes this sequence locally and
    requests a display repaint from daemon state, so gate it to ZMX provider
    rows instead of using it as a generic terminal fallback.
    */
    public static boolean shouldRefreshAfterSidebarSwitch(@Nullable GhostexRemoteSession remoteSession,
                                                          @Nullable TerminalSession terminalSession) {
        return remoteSession != null && remoteSession.isZmxBacked() &&
            terminalSession != null && terminalSession.isRunning();
    }

    @NonNull
    public static String sequence() {
        return REFRESH_SEQUENCE;
    }
}
