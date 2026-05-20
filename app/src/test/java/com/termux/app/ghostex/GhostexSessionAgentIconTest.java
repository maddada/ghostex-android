package com.termux.app.ghostex;

import com.termux.R;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class GhostexSessionAgentIconTest {

    @Test
    public void resolveIconIdPrefersCliAgentIconField() {
        Assert.assertEquals("codex", GhostexSessionAgentIcon.resolveIconId("codex", "terminal"));
    }

    @Test
    public void resolveIconIdFallsBackToKnownAgentNames() {
        Assert.assertEquals("cursor-cli", GhostexSessionAgentIcon.resolveIconId(null, "Cursor CLI"));
        Assert.assertEquals("claude", GhostexSessionAgentIcon.resolveIconId("", "claude code"));
    }

    @Test
    public void drawableUsesTerminalFallbackWhenNoAgentIdentity() {
        GhostexRemoteSession session = new GhostexRemoteSession("1", "session-1", "project-1",
            "Plain", "Ghostex", "/tmp", "idle", "running", "zmx", "zmx-main", "",
            "", "2026-05-17T10:00:00Z", false, false);

        Assert.assertEquals(R.drawable.ic_ghostex_agent_terminal,
            GhostexSessionAgentIcon.drawableResForSession(session));
    }

    @Test
    public void drawableUsesAgentIconFieldFromInventory() {
        GhostexRemoteSession session = new GhostexRemoteSession("1", "session-1", "project-1",
            "Codex task", "Ghostex", "/tmp", "idle", "running", "zmx", "zmx-main", "codex",
            "codex", "2026-05-17T10:00:00Z", false, false);

        Assert.assertEquals(R.drawable.ic_ghostex_agent_codex,
            GhostexSessionAgentIcon.drawableResForSession(session));
    }
}
