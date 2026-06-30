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

    @Test
    public void delayedAttachRefreshRequiresVisibleRenderedTerminal() {
        /*
        CDXC:AndroidRemoteAttachLatency 2026-06-30-19:16:
        A remote ZMX attach can spend time opening SSH and starting either direct `zmx attach` or fallback `ghostex attach`.
        The automatic refresh should not start its final two-second delay until the selected Android terminal is measured, visible, backed by an emulator, and has rendered remote output.
        */
        Assert.assertFalse(GhostexZmxViewportRefresh.isAttachVisibleForDelayedRefresh(
            0, 480, true, true, true, true));
        Assert.assertFalse(GhostexZmxViewportRefresh.isAttachVisibleForDelayedRefresh(
            320, 480, false, true, true, true));
        Assert.assertFalse(GhostexZmxViewportRefresh.isAttachVisibleForDelayedRefresh(
            320, 480, true, false, true, true));
        Assert.assertFalse(GhostexZmxViewportRefresh.isAttachVisibleForDelayedRefresh(
            320, 480, true, true, false, true));
        Assert.assertFalse(GhostexZmxViewportRefresh.isAttachVisibleForDelayedRefresh(
            320, 480, true, true, true, false));
        Assert.assertTrue(GhostexZmxViewportRefresh.isAttachVisibleForDelayedRefresh(
            320, 480, true, true, true, true));
    }

    @Test
    public void delayedAttachRefreshUsesTwoSecondPostVisibleDelay() {
        Assert.assertEquals(2000L, GhostexZmxViewportRefresh.POST_ATTACH_REFRESH_DELAY_MS);
        Assert.assertEquals(250L, GhostexZmxViewportRefresh.ATTACH_VISIBLE_RETRY_DELAY_MS);
        Assert.assertTrue(GhostexZmxViewportRefresh.MAX_ATTACH_VISIBLE_ATTEMPTS > GhostexZmxViewportRefresh.MAX_VIEWPORT_READY_ATTEMPTS);
    }

    @Test
    public void renderedContentCheckDoesNotRequireReadingSpecificTerminalText() {
        Assert.assertFalse(GhostexZmxViewportRefresh.hasVisibleTerminalContent(null));
        Assert.assertFalse(GhostexZmxViewportRefresh.hasVisibleTerminalContent(" \r\n\t"));
        Assert.assertTrue(GhostexZmxViewportRefresh.hasVisibleTerminalContent("zmx"));
    }

    @Test
    public void pageUpPageDownSequencesMatchManualRefreshNudge() {
        /*
        CDXC:AndroidRemoteAttach 2026-06-22-05:46:
        The delayed automatic ZMX attach refresh should use the same PageUp/PageDown bytes as the manual Android refresh control after the post-visible delay, so users do not need to press those keys themselves to repair stale dimensions.
        */
        Assert.assertEquals("\u001B[5~", GhostexZmxViewportRefresh.pageUpSequence(false, false));
        Assert.assertEquals("\u001B[6~", GhostexZmxViewportRefresh.pageDownSequence(false, false));
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
