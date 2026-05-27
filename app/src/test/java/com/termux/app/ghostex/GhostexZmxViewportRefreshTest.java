package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexZmxViewportRefreshTest {

    /*
    CDXC:AndroidRemoteAttach 2026-05-26-20:32:
    Android sidebar session switches must use the same zmx-only private refresh
    sequence as macOS. Keep the byte contract tested because sending a wrong or
    non-zmx sequence would either do nothing or leak control bytes to a shell.
    */
    @Test
    public void sequenceMatchesZmxPrivateRefreshOsc() {
        Assert.assertEquals("\u001B]1337;ZMX_REFRESH\u0007", GhostexZmxViewportRefresh.sequence());
    }

    @Test
    public void zmxBackedSessionsAreEligibleForSwitchRefresh() {
        Assert.assertTrue(session("zmx").isZmxBacked());
        Assert.assertFalse(session("tmux").isZmxBacked());
    }

    @Test
    public void waitsForMeasuredTerminalViewBeforeRefresh() {
        /*
        CDXC:AndroidNotifications 2026-05-27-05:31:
        Notification session taps can foreground the Activity before the terminal view is measured. The zmx refresh should wait for nonzero dimensions so the attach resize and refresh are not lost.
        */
        Assert.assertFalse(GhostexZmxViewportRefresh.isTerminalViewReadyForRefresh(0, 480));
        Assert.assertFalse(GhostexZmxViewportRefresh.isTerminalViewReadyForRefresh(320, 0));
        Assert.assertTrue(GhostexZmxViewportRefresh.isTerminalViewReadyForRefresh(320, 480));
        Assert.assertEquals(6, GhostexZmxViewportRefresh.MAX_VIEWPORT_READY_ATTEMPTS);
    }

    private GhostexRemoteSession session(String provider) {
        return new GhostexRemoteSession(
            "1",
            "session-1",
            "project-1",
            "Work",
            "Ghostex",
            "/Users/madda/dev/ghostex",
            "idle",
            "running",
            provider,
            provider + "-work",
            "codex",
            "2026-05-26T20:32:00Z",
            false,
            false
        );
    }
}
