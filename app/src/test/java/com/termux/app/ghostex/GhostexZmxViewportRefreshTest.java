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
