package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;

public final class GhostexWarmSessionPolicyTest {

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-17:55:
    Keep lifecycle eviction rules explicit: only remote actions that invalidate
    the attached ZMX runtime should close a warm terminal surface.

    CDXC:AndroidRemoteSessions 2026-05-17-14:30:
    The warm cache is access-order. Dead-session cleanup must not use map
    lookups while iterating because that would mutate click recency and can
    destabilize iteration.

    CDXC:AndroidRemoteSessions 2026-05-17-16:22:
    Rehydration overflow must keep the current attach terminal in the warm map
    even if it appears oldest after rebuilding the cache from TermuxService.
    */

    @Test
    public void killAndSleepCloseWarmSessions() {
        Assert.assertTrue(GhostexWarmSessionPolicy.shouldCloseWarmSessionForAction("kill"));
        Assert.assertTrue(GhostexWarmSessionPolicy.shouldCloseWarmSessionForAction("sleep"));
    }

    @Test
    public void nonLifecycleInvalidatingActionsKeepWarmSessions() {
        Assert.assertFalse(GhostexWarmSessionPolicy.shouldCloseWarmSessionForAction("wake"));
        Assert.assertFalse(GhostexWarmSessionPolicy.shouldCloseWarmSessionForAction("focus"));
        Assert.assertFalse(GhostexWarmSessionPolicy.shouldCloseWarmSessionForAction("rename"));
    }

    @Test
    public void deadWarmSessionKeysPreservesAccessOrderWhileCollecting() {
        LinkedHashMap<String, Boolean> sessions = new LinkedHashMap<>(8, 0.75f, true);
        sessions.put("old-running", true);
        sessions.put("dead", false);
        sessions.put("new-running", true);
        sessions.get("old-running");

        List<String> deadKeys = GhostexWarmSessionPolicy.deadWarmSessionKeys(sessions,
            value -> value != null && value);

        Assert.assertEquals(1, deadKeys.size());
        Assert.assertEquals("dead", deadKeys.get(0));
        Assert.assertArrayEquals(new String[] { "dead", "new-running", "old-running" },
            sessions.keySet().toArray(new String[0]));
    }

    @Test
    public void overflowWarmSessionKeysPreservesProtectedCurrentSession() {
        LinkedHashMap<String, Boolean> sessions = new LinkedHashMap<>(8, 0.75f, true);
        sessions.put("current", true);
        sessions.put("old-1", true);
        sessions.put("old-2", true);
        sessions.put("old-3", true);

        List<String> overflowKeys = GhostexWarmSessionPolicy.overflowWarmSessionKeys(
            sessions, 2, "current");

        Assert.assertEquals(2, overflowKeys.size());
        Assert.assertEquals("old-1", overflowKeys.get(0));
        Assert.assertEquals("old-2", overflowKeys.get(1));
    }

}
