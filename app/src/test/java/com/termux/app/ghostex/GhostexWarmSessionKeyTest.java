package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexWarmSessionKeyTest {

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-17:34:
    Warm session keys protect multi-machine switching. Test same-session-id
    cases explicitly so the LRU cache cannot regress into cross-machine reuse.

    CDXC:AndroidSidebar 2026-05-17-12:29:
    The active drawer row uses the same machine-scoped key as the warm terminal
    cache, so tests cover both direct key creation and the adapter predicate.
    */

    @Test
    public void forIdsMatchesMachineSessionKeyFormat() {
        GhostexMachine machine = machine("machine-a");
        GhostexRemoteSession session = session("session-a");

        Assert.assertEquals(GhostexWarmSessionKey.forSession(machine, session),
            GhostexWarmSessionKey.forIds(machine.id, session.sessionId));
    }

    @Test
    public void keysDifferForDifferentMachinesWithSameRemoteSessionId() {
        GhostexRemoteSession session = session("shared-session");

        String first = GhostexWarmSessionKey.forSession(machine("machine-a"), session);
        String second = GhostexWarmSessionKey.forSession(machine("machine-b"), session);

        Assert.assertNotEquals(first, second);
        Assert.assertTrue(GhostexWarmSessionKey.belongsToMachine(first, "machine-a"));
        Assert.assertFalse(GhostexWarmSessionKey.belongsToMachine(first, "machine-b"));
    }

    @Test
    public void keysDifferForDifferentRemoteSessionsOnSameMachine() {
        GhostexMachine machine = machine("machine-a");

        String first = GhostexWarmSessionKey.forSession(machine, session("session-a"));
        String second = GhostexWarmSessionKey.forSession(machine, session("session-b"));

        Assert.assertNotEquals(first, second);
        Assert.assertTrue(GhostexWarmSessionKey.belongsToMachine(first, machine.id));
        Assert.assertTrue(GhostexWarmSessionKey.belongsToMachine(second, machine.id));
    }

    @Test
    public void activeSessionRequiresMatchingMachineScopedKey() {
        GhostexRemoteSession session = session("shared-session");
        String activeKey = GhostexWarmSessionKey.forIds("machine-a", session.sessionId);

        Assert.assertTrue(GhostexRemoteSessionAdapter.isActiveSession("machine-a", activeKey, session));
        Assert.assertFalse(GhostexRemoteSessionAdapter.isActiveSession("machine-b", activeKey, session));
        Assert.assertFalse(GhostexRemoteSessionAdapter.isActiveSession("machine-a", null, session));
    }

    private GhostexMachine machine(String id) {
        return new GhostexMachine(id, "Mac " + id, id + ".tailnet.ts.net", "madda", 22, false, 0L);
    }

    private GhostexRemoteSession session(String sessionId) {
        return new GhostexRemoteSession("1", sessionId, "project-1", "Session", "Project",
            "/Users/madda/dev/project", "idle", "idle", "zmx", "zmx-session", "codex",
            "2026-05-17T10:00:00Z", false, false);
    }

}
