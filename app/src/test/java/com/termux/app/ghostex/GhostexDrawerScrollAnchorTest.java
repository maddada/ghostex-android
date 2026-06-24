package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class GhostexDrawerScrollAnchorTest {

    /*
    CDXC:AndroidSidebar 2026-05-19-10:45:
    Inventory refreshes restore scroll by stable project/session keys so row
    inserts above the viewport do not jump the drawer back to the top.
    */
    @Test
    public void drawerScrollAnchorKeyUsesStableProjectAndSessionIds() {
        GhostexRemoteSession session = session("1", "project-1");
        GhostexDrawerItem projectHeader = GhostexDrawerItem.buildItems(
            java.util.Collections.singletonList(session)).get(0);
        GhostexDrawerItem sessionRow = GhostexDrawerItem.buildItems(
            java.util.Collections.singletonList(session)).get(1);

        Assert.assertEquals("project:" + projectHeader.projectKey,
            GhostexDrawerScrollAnchor.keyForItem(projectHeader));
        Assert.assertEquals("session:session-1",
            GhostexDrawerScrollAnchor.keyForItem(sessionRow));
    }

    @Test
    public void drawerScrollAnchorKeyUsesStableProjectToggleId() {
        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessionsInProject(8));
        GhostexDrawerItem toggle = items.get(items.size() - 1);

        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_SESSION_LIST_TOGGLE, toggle.type);
        Assert.assertEquals("project-session-toggle:" + toggle.projectKey,
            GhostexDrawerScrollAnchor.keyForItem(toggle));
    }

    @Test
    public void firstStableVisibleAnchorSkipsRowsWithoutStableKeys() {
        /*
        CDXC:AndroidSidebar 2026-06-23-08:27:
        The sidebar open cycle should remember the first stable visible project/session row instead of losing the viewport when child zero is a transient state or action row.
        */
        ArrayList<GhostexDrawerItem> items = new ArrayList<>();
        items.add(GhostexDrawerItem.stateCard("Connecting", "Loading sessions.", "Wait."));
        items.addAll(GhostexDrawerItem.buildItems(java.util.Collections.singletonList(
            session("1", "project-1"))));

        GhostexDrawerScrollAnchor anchor = GhostexDrawerScrollAnchor.firstStableVisibleAnchor(
            items, 0, 3, childIndex -> childIndex == 1 ? 48 : 96);

        Assert.assertNotNull(anchor);
        Assert.assertEquals("project:" + items.get(1).projectKey, anchor.anchorKey);
        Assert.assertEquals(48, anchor.topOffset);
    }

    private static ArrayList<GhostexRemoteSession> sessionsInProject(int count) {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            sessions.add(session(String.valueOf(index + 1), "project-1"));
        }
        return sessions;
    }

    private static GhostexRemoteSession session(String alias, String projectId) {
        return new GhostexRemoteSession(alias, "session-" + alias, projectId,
            "Session " + alias, "Ghostex", "/Users/madda/dev/_active/zmux", "working", "running", "zmx",
            "zmx-main", "codex", "2026-05-17T10:00:00Z", false, false);
    }

}
