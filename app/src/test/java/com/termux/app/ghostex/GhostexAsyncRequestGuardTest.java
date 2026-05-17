package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexAsyncRequestGuardTest {

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-20:06:
    Async SSH results must be scoped by generation and machine id so switching
    saved machines cannot display stale sessions or stale password prompts.

    CDXC:AndroidConnectionManagement 2026-05-17-13:52:
    Non-destructive machine checks still need stale-result suppression because
    the latest readiness probe owns the visible setup and credential recovery
    flow.
    */
    @Test
    public void acceptsOnlyLatestReconnectForSelectedMachine() {
        Assert.assertTrue(GhostexAsyncRequestGuard.isCurrentReconnect(4L, 4L, "mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentReconnect(3L, 4L, "mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentReconnect(4L, 4L, "mac-a", "mac-b"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentReconnect(4L, 4L, "mac-a", null));
    }

    @Test
    public void acceptsActionsOnlyForStillSelectedMachine() {
        Assert.assertTrue(GhostexAsyncRequestGuard.isCurrentMachine("mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentMachine("mac-a", "mac-b"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentMachine("mac-a", null));
    }

    @Test
    public void acceptsOnlyLatestGeneratedRequest() {
        Assert.assertTrue(GhostexAsyncRequestGuard.isLatestRequest(7L, 7L));
        Assert.assertFalse(GhostexAsyncRequestGuard.isLatestRequest(6L, 7L));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:47:
    Remote actions and attach preflights must expire when the user switches
    machines away and back before the SSH callback returns.
    */
    @Test
    public void acceptsOnlyLatestMachineScopedRequest() {
        Assert.assertTrue(GhostexAsyncRequestGuard.isCurrentMachineRequest(5L, 5L, "mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentMachineRequest(4L, 5L, "mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentMachineRequest(5L, 5L, "mac-a", "mac-b"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isCurrentMachineRequest(5L, 5L, "mac-a", null));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:52:
    Matching machine and generation are insufficient after activity teardown.
    Queued callbacks must expire when the controller is no longer alive.
    */
    @Test
    public void rejectsMatchingRequestAfterControllerDestroy() {
        Assert.assertTrue(GhostexAsyncRequestGuard.isLiveMachineRequest(true, 8L, 8L, "mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isLiveMachineRequest(false, 8L, 8L, "mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isLiveMachineRequest(true, 7L, 8L, "mac-a", "mac-a"));
        Assert.assertFalse(GhostexAsyncRequestGuard.isLiveMachineRequest(true, 8L, 8L, "mac-a", "mac-b"));
    }

}
