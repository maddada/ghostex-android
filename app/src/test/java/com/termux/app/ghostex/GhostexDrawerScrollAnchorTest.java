package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexDrawerScrollAnchorTest {

    /*
    CDXC:AndroidSidebar 2026-05-19-10:45:
    Inventory refreshes restore scroll by stable project/session keys so row
    inserts above the viewport do not jump the drawer back to the top.
    */
    @Test
    public void drawerScrollAnchorKeyUsesStableProjectAndSessionIds() {
        GhostexRemoteSession session = new GhostexRemoteSession("1", "session-1", "project-1",
            "Session 1", "Ghostex", "/Users/madda/dev/_active/zmux", "working", "running", "zmx",
            "zmx-main", "codex", "2026-05-17T10:00:00Z", false, false);
        GhostexDrawerItem projectHeader = GhostexDrawerItem.buildItems(
            java.util.Collections.singletonList(session)).get(0);
        GhostexDrawerItem sessionRow = GhostexDrawerItem.buildItems(
            java.util.Collections.singletonList(session)).get(1);

        Assert.assertEquals("project:" + projectHeader.projectKey,
            GhostexDrawerScrollAnchor.keyForItem(projectHeader));
        Assert.assertEquals("session:session-1",
            GhostexDrawerScrollAnchor.keyForItem(sessionRow));
    }

}
