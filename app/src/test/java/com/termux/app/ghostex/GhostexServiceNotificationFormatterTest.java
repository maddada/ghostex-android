package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public final class GhostexServiceNotificationFormatterTest {

    /*
    CDXC:AndroidReleaseSurface 2026-05-17-13:19:
    Notification copy should stay Ghostex-specific even though Termux still
    owns the underlying foreground service implementation.
    */
    @Test
    public void formatsGhostexRemoteAttachState() {
        Assert.assertEquals("Ready to connect to Ghostex sessions",
            GhostexServiceNotificationFormatter.buildText(0, 0, false));
        Assert.assertEquals("1 remote terminal",
            GhostexServiceNotificationFormatter.buildText(1, 0, false));
        Assert.assertEquals("2 remote terminals, 1 background operation",
            GhostexServiceNotificationFormatter.buildText(2, 1, false));
        Assert.assertEquals("1 remote terminal · keeping connection awake",
            GhostexServiceNotificationFormatter.buildText(1, 0, true));
    }

    @Test
    public void prioritizesActionableNotificationRows() {
        /*
        CDXC:AndroidNotifications 2026-05-23-14:40:
        Notification shade rows must expose actionable state before ordinary
        idle/running sessions, otherwise a Mac-visible working indicator can be
        hidden below newer idle inventory rows on Android.
        */
        ArrayList<GhostexRemoteSession> sessions = GhostexServiceNotificationFormatter.sortForNotification(Arrays.asList(
            session("working-new", "working", "running", "2026-05-21T19:25:00Z"),
            session("done-old", "done", "exited", "2026-05-21T18:00:00Z"),
            session("idle-newer", "idle", "running", "2026-05-21T19:30:00Z"),
            session("attention", "attention", "running", "2026-05-21T17:00:00Z")
        ));

        Assert.assertEquals("session-done-old", sessions.get(0).sessionId);
        Assert.assertEquals("session-attention", sessions.get(1).sessionId);
        Assert.assertEquals("session-working-new", sessions.get(2).sessionId);
        Assert.assertEquals("session-idle-newer", sessions.get(3).sessionId);
    }

    @Test
    public void choosesSingleStatusIndicatorCountByDoneWorkingRunningPrecedence() {
        GhostexServiceNotificationFormatter.StatusIndicator done =
            GhostexServiceNotificationFormatter.statusIndicator(Arrays.asList(
                session("working", "working", "running", "2026-05-21T18:02:00Z"),
                session("done", "done", "exited", "2026-05-21T18:00:00Z"),
                session("idle", "idle", "running", "2026-05-21T18:03:00Z")
            ));
        Assert.assertEquals("done", done.state);
        Assert.assertEquals(1, done.count);

        GhostexServiceNotificationFormatter.StatusIndicator working =
            GhostexServiceNotificationFormatter.statusIndicator(Arrays.asList(
                session("working", "working", "running", "2026-05-21T18:02:00Z"),
                session("idle", "idle", "running", "2026-05-21T18:03:00Z")
            ));
        Assert.assertEquals("working", working.state);
        Assert.assertEquals(1, working.count);

        GhostexServiceNotificationFormatter.StatusIndicator running =
            GhostexServiceNotificationFormatter.statusIndicator(Arrays.asList(
                session("idle-a", "idle", "running", "2026-05-21T18:03:00Z"),
                session("idle-b", "idle", "running", "2026-05-21T18:04:00Z")
            ));
        Assert.assertEquals("running", running.state);
        Assert.assertEquals(2, running.count);
    }

    @Test
    public void treatsAttentionAsAudibleNotificationState() {
        /*
        CDXC:AndroidNotifications 2026-05-26-14:42:
        Android must play the status sound when a known remote session moves into attention, not only when it reaches Done.
        */
        Assert.assertTrue(GhostexServiceNotificationFormatter.shouldPlayStatusSound(
            session("attention", "attention", "running", "2026-05-26T10:42:00Z")));
        Assert.assertTrue(GhostexServiceNotificationFormatter.shouldPlayStatusSound(
            session("done", "done", "exited", "2026-05-26T10:43:00Z")));
        Assert.assertFalse(GhostexServiceNotificationFormatter.shouldPlayStatusSound(
            session("working", "working", "running", "2026-05-26T10:44:00Z")));
    }

    private GhostexRemoteSession session(String alias, String activity, String status, String lastInteractionAt) {
        return new GhostexRemoteSession(alias, "session-" + alias, "project-1", "Session " + alias,
            "Ghostex", "/Users/madda/dev/_active/zmux", activity, status, "zmx",
            "zmx-main", "codex", lastInteractionAt, false, false);
    }

}
