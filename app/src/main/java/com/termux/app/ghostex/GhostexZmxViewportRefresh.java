package com.termux.app.ghostex;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalSession;

public final class GhostexZmxViewportRefresh {

    private static final String REFRESH_SEQUENCE = "\u001B]1337;ZMX_REFRESH\u0007";
    public static final int MAX_VIEWPORT_READY_ATTEMPTS = 6;
    public static final int MAX_ATTACH_VISIBLE_ATTEMPTS = 80;
    public static final long ATTACH_VISIBLE_RETRY_DELAY_MS = 250L;
    public static final long POST_ATTACH_REFRESH_DELAY_MS = 2000L;

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

    /*
    CDXC:AndroidRemoteAttach 2026-06-22-05:24:
    The automatic ZMX viewport refresh must be a post-attach redraw, not an attach-start timer.
    Wait until the selected Android terminal is visible, measured, has an emulator, and has rendered remote output, then wait about two seconds before sending the existing zmx refresh sequence.
    Do not log terminal text while making this decision; only reduce the screen snapshot to a boolean.
    */
    public static boolean isAttachVisibleForDelayedRefresh(int widthPixels,
                                                           int heightPixels,
                                                           boolean terminalViewShown,
                                                           boolean activityVisible,
                                                           boolean emulatorAttached,
                                                           boolean hasRenderedRemoteOutput) {
        return isTerminalViewReadyForRefresh(widthPixels, heightPixels) &&
            terminalViewShown &&
            activityVisible &&
            emulatorAttached &&
            hasRenderedRemoteOutput;
    }

    public static boolean hasVisibleTerminalContent(@Nullable String terminalText) {
        if (terminalText == null) return false;
        for (int i = 0; i < terminalText.length(); i++) {
            if (!Character.isWhitespace(terminalText.charAt(i))) return true;
        }
        return false;
    }

    @Nullable
    public static String pageUpSequence(boolean cursorApplicationMode, boolean keypadApplicationMode) {
        return KeyHandler.getCode(KeyEvent.KEYCODE_PAGE_UP, 0, cursorApplicationMode, keypadApplicationMode);
    }

    @Nullable
    public static String pageDownSequence(boolean cursorApplicationMode, boolean keypadApplicationMode) {
        return KeyHandler.getCode(KeyEvent.KEYCODE_PAGE_DOWN, 0, cursorApplicationMode, keypadApplicationMode);
    }

    @NonNull
    public static String sequence() {
        return REFRESH_SEQUENCE;
    }
}
