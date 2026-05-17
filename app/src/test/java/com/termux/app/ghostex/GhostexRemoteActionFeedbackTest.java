package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexRemoteActionFeedbackTest {

    /*
    CDXC:AndroidSidebar 2026-05-17-14:27:
    Context-menu success status is part of the polished Android drawer UX.
    Successful remote actions should confirm the actual action after refresh
    instead of replacing user intent with generic reconnect copy.
    */
    @Test
    public void singleActionFeedbackNamesTheActionAndSession() {
        GhostexRemoteSession session = session("7");

        Assert.assertEquals("Focused session 7 on the Mac.",
            GhostexRemoteActionFeedback.singleSuccess("focus", session));
        Assert.assertEquals("Woke session 7.",
            GhostexRemoteActionFeedback.singleSuccess("wake", session));
        Assert.assertEquals("Slept session 7.",
            GhostexRemoteActionFeedback.singleSuccess("sleep", session));
        Assert.assertEquals("Killed session 7.",
            GhostexRemoteActionFeedback.singleSuccess("kill", session));
    }

    @Test
    public void projectActionFeedbackIncludesCountAndProject() {
        Assert.assertEquals("Woke 2 sessions in Ghostex.",
            GhostexRemoteActionFeedback.projectSuccess("wake", "Ghostex", 2));
        Assert.assertEquals("Slept 1 session in Ghostex.",
            GhostexRemoteActionFeedback.projectSuccess("sleep", "Ghostex", 1));
        Assert.assertEquals("Killed 3 sessions in Ghostex.",
            GhostexRemoteActionFeedback.projectSuccess("kill", "Ghostex", 3));
    }

    @Test
    public void renameFeedbackUsesStableAliasCopy() {
        Assert.assertEquals("Renamed session 7.",
            GhostexRemoteActionFeedback.renameSuccess(session("7")));
    }

    private GhostexRemoteSession session(String alias) {
        return new GhostexRemoteSession(alias, "session-" + alias, "project-1", "Session " + alias,
            "Ghostex", "/Users/madda/dev/_active/zmux", "idle", "idle", "zmx",
            "zmx-" + alias, "codex", "2026-05-17T10:00:00Z", false, false);
    }

}
