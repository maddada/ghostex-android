package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

public final class GhostexZmxViewportRefresh {

    private static final String REFRESH_SEQUENCE = "\u001B]1337;ZMX_REFRESH\u0007";
    public static final int MAX_VIEWPORT_READY_ATTEMPTS = 6;

    private GhostexZmxViewportRefresh() {}

    /*
    CDXC:AndroidRemoteAttach 2026-05-26-20:32:
    Selecting a ZMX-backed session from the Android sidebar should mirror the
    macOS focus/surface behavior by sending zmx's private refresh OSC once
    after the terminal becomes current. zmx consumes this sequence locally and
    requests a display repaint from daemon state, so gate it to ZMX provider
    rows instead of using it as a generic terminal fallback.

    CDXC:AndroidNotifications 2026-05-27-05:31:
    Notification row session activation is another session-switch surface and
    must use the same zmx-only refresh eligibility as sidebar taps.
    */
    public static boolean shouldRefreshAfterSessionSwitch(@Nullable GhostexRemoteSession remoteSession,
                                                          @Nullable TerminalSession terminalSession) {
        return remoteSession != null && remoteSession.isZmxBacked() &&
            terminalSession != null && terminalSession.isRunning();
    }

    /*
    CDXC:AndroidNotifications 2026-05-27-05:31:
    Notification row taps can cold-start or foreground Ghostex Android before
    TerminalView has nonzero dimensions. Wait for a real terminal view size
    before sending the one zmx viewport refresh so the remote attach receives
    the same resize/refresh behavior as a sidebar session tap.
    */
    public static boolean isTerminalViewReadyForRefresh(int widthPixels, int heightPixels) {
        return widthPixels > 0 && heightPixels > 0;
    }

    @NonNull
    public static String sequence() {
        return REFRESH_SEQUENCE;
    }
}
