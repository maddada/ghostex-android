package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public final class GhostexRemoteActionSummaryTest {

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-12:56:
    Project actions must preserve partial-success evidence so lifecycle
    cleanup can close warm terminals for sessions that really changed even
    when another SSH action reports a failure.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:05:
    Password-recovery retries for project actions must retry only failed rows,
    so the summary needs copy-safe failed-session and credential-error evidence.
    */

    @Test
    public void recordsSuccessesAndLastFailureSeparately() {
        GhostexRemoteActionSummary summary = new GhostexRemoteActionSummary();
        GhostexRemoteSession first = session("one");
        GhostexRemoteSession second = session("two");

        summary.record(first, GhostexSessionInventoryClient.Result.success(new ArrayList<>()));
        summary.record(second, GhostexSessionInventoryClient.Result.failure("Could not sleep session two."));

        Assert.assertTrue(summary.hasSuccesses());
        Assert.assertTrue(summary.hasFailures());
        Assert.assertEquals(1, summary.successfulSessions().size());
        Assert.assertEquals("session-one", summary.successfulSessions().get(0).sessionId);
        Assert.assertEquals(1, summary.failedSessions().size());
        Assert.assertEquals("session-two", summary.failedSessions().get(0).sessionId);
        Assert.assertEquals("Could not sleep session two.", summary.lastErrorMessage());
    }

    @Test
    public void successfulSessionsReturnsACopy() {
        GhostexRemoteActionSummary summary = new GhostexRemoteActionSummary();
        summary.record(session("one"), GhostexSessionInventoryClient.Result.success(new ArrayList<>()));
        summary.record(session("two"), GhostexSessionInventoryClient.Result.failure("Could not sleep session two."));

        summary.successfulSessions().clear();
        summary.failedSessions().clear();

        Assert.assertEquals(1, summary.successfulSessions().size());
        Assert.assertEquals(1, summary.failedSessions().size());
    }

    @Test
    public void recordsFirstCredentialFailureForPasswordRecovery() {
        GhostexRemoteActionSummary summary = new GhostexRemoteActionSummary();

        summary.record(session("one"), GhostexSessionInventoryClient.Result.failure(
            "SSH needs a key or password. Open the machine settings and save a password, or configure SSH keys/Tailscale SSH."));
        summary.record(session("two"), GhostexSessionInventoryClient.Result.failure("Connection timed out."));

        Assert.assertEquals(
            "SSH needs a key or password. Open the machine settings and save a password, or configure SSH keys/Tailscale SSH.",
            summary.credentialErrorMessage());
        Assert.assertEquals("Connection timed out.", summary.lastErrorMessage());
    }

    private GhostexRemoteSession session(String alias) {
        return new GhostexRemoteSession(alias, "session-" + alias, "project-1", "Session " + alias,
            "Ghostex", "/Users/madda/dev/_active/zmux", "idle", "idle", "zmx",
            "zmx-" + alias, "codex", "2026-05-17T10:00:00Z", false, false);
    }

}
