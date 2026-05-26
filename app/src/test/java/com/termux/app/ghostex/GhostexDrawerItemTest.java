package com.termux.app.ghostex;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class GhostexDrawerItemTest {

    /*
    CDXC:AndroidSidebar 2026-05-17-10:43:
    The Android sidebar must keep project grouping from the Ghostex CLI
    inventory. Unit-test the grouping model separately from Android views so
    release builds do not silently regress to a flat session list.

    CDXC:AndroidConnectionManagement 2026-05-17-13:02:
    Drawer state cards are part of the recovery contract for setup, failed
    reconnect, and empty-session states, so test their model fields alongside
    the grouped session rows.

    CDXC:AndroidConnectionRecovery 2026-05-17-12:35:
    State cards are actionable recovery rows. Keep adapter coverage around
    enabled state cards so tap-to-repair does not regress into inert copy.

    CDXC:AndroidSidebar 2026-05-17-12:45:
    Grouped Android drawer rows should match the macOS sidebar order inside
    each project: attention first, then working, then most recently active.

    CDXC:AndroidRemoteSessions 2026-05-17-12:52:
    Remote provider and lifecycle tokens should be normalized before Android
    filters to ZMX so case differences in CLI payloads do not hide sessions.

    CDXC:AndroidRemoteSessions 2026-05-17-13:57:
    The macOS Ghostex CLI currently emits `provider` and `providerSessionName`,
    while earlier sidebar-shaped payloads used `sessionPersistenceProvider` and
    `sessionPersistenceName`. Android should accept both contracts.

    CDXC:AndroidRemoteSessions 2026-05-17-15:33:
    Alias is a display badge, not the remote command selector. If the CLI
    omits alias while still returning a stable session id, Android should keep
    the row visible with an id-derived badge instead of dropping the session.

    CDXC:AndroidRemoteSessions 2026-05-17-17:49:
    CLI JSON nulls should behave like missing optional fields. Android should
    not show "null" as an alias/title/project label in the release drawer.

    CDXC:AndroidSidebar 2026-05-17-18:01:
    Missing project metadata should not merge unrelated sessions into one
    project action target. Keep unknown-project groups scoped by session id.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Project headers preserve the CLI-provided desktop sidebar order. Android only
    sorts sessions inside each project, because moving project placement must be
    persisted by the desktop app and reflected back through the CLI inventory.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Project collapse is a local Android disclosure state keyed by project group,
    so a collapsed project should keep its header and counts while hiding child
    sessions across drawer rebuilds.

    CDXC:AndroidSidebar 2026-05-17-20:03:
    Live macOS sidebar inventory can carry `groupId` and `groupTitle`. Android
    should use them as fallback grouping metadata so valid grouped rows do not
    become per-session Ungrouped cards when project fields are sparse.

    CDXC:AndroidRemoteSessions 2026-05-18-02:31:
    Project header rows must retain the remote project and group ids so the
    Android plus button can ask the Mac Ghostex CLI to create a session in the
    tapped sidebar group.
    */

    @Test
    public void buildItemsCreatesProjectHeadersBeforeSessions() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(session("1", "project-a", "Ghostex", "working", false));
        sessions.add(session("2", "project-a", "Ghostex", "idle", true));
        sessions.add(session("3", "project-b", "Website", "attention", false));

        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions);

        Assert.assertEquals(5, items.size());
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_HEADER, items.get(0).type);
        Assert.assertEquals("Ghostex", items.get(0).projectTitle);
        Assert.assertEquals("project-a", items.get(0).projectId);
        Assert.assertEquals(2, items.get(0).sessionCount);
        Assert.assertEquals(1, items.get(0).workingCount);
        Assert.assertEquals(1, items.get(0).sleepingCount);
        Assert.assertEquals(GhostexDrawerItem.Type.SESSION, items.get(1).type);
        Assert.assertEquals(GhostexDrawerItem.Type.SESSION, items.get(2).type);
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_HEADER, items.get(3).type);
        Assert.assertEquals("Website", items.get(3).projectTitle);
        Assert.assertEquals("project-b", items.get(3).projectId);
        Assert.assertEquals(1, items.get(3).attentionCount);
        Assert.assertEquals(GhostexDrawerItem.Type.SESSION, items.get(4).type);
    }

    @Test
    public void buildItemsSortsSessionsInsideProjectLikeMacSidebar() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(session("idle-new", "project-a", "Ghostex", "idle", false, "2026-05-17T08:59:00Z"));
        sessions.add(session("working-old", "project-a", "Ghostex", "working", false, "2026-05-17T08:00:00Z"));
        sessions.add(session("attention-old", "project-a", "Ghostex", "attention", false, "2026-05-17T07:00:00Z"));
        sessions.add(session("idle-old", "project-a", "Ghostex", "idle", false, "2026-05-17T08:00:00Z"));

        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions);

        Assert.assertEquals("session-attention-old", items.get(1).session.sessionId);
        Assert.assertEquals("session-working-old", items.get(2).session.sessionId);
        Assert.assertEquals("session-idle-new", items.get(3).session.sessionId);
        Assert.assertEquals("session-idle-old", items.get(4).session.sessionId);
    }

    @Test
    public void buildItemsPreservesCliProjectGroupOrder() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(session("idle-new", "project-idle", "Idle Project", "idle", false, "2026-05-17T08:59:00Z"));
        sessions.add(session("working-old", "project-working", "Working Project", "working", false, "2026-05-17T08:00:00Z"));
        sessions.add(session("attention-old", "project-attention", "Attention Project", "attention", false, "2026-05-17T07:00:00Z"));

        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions);

        Assert.assertEquals("Idle Project", items.get(0).projectTitle);
        Assert.assertEquals("Working Project", items.get(2).projectTitle);
        Assert.assertEquals("Attention Project", items.get(4).projectTitle);
    }

    @Test
    public void buildItemsHidesCollapsedProjectSessions() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(session("1", "project-a", "Ghostex", "working", false));
        sessions.add(session("2", "project-a", "Ghostex", "idle", false));
        sessions.add(session("3", "project-b", "Website", "idle", false));
        java.util.HashSet<String> collapsedProjectKeys = new java.util.HashSet<>();
        collapsedProjectKeys.add("id:project-a");

        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions, collapsedProjectKeys);

        Assert.assertEquals(3, items.size());
        Assert.assertEquals("Ghostex", items.get(0).projectTitle);
        Assert.assertTrue(items.get(0).collapsed);
        Assert.assertEquals(2, items.get(0).sessionCount);
        Assert.assertEquals("Website", items.get(1).projectTitle);
        Assert.assertFalse(items.get(1).collapsed);
        Assert.assertEquals(GhostexDrawerItem.Type.SESSION, items.get(2).type);
    }

    @Test
    public void buildItemsAddsMacStyleShowLessToggleForLongProjectLists() {
        ArrayList<GhostexRemoteSession> sessions = sessionsInProject("project-a", "Ghostex", 8);

        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions);

        Assert.assertEquals(10, items.size());
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_HEADER, items.get(0).type);
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_SESSION_LIST_TOGGLE, items.get(9).type);
        Assert.assertFalse(items.get(9).sessionListCollapsed);
        Assert.assertEquals(8, items.get(9).sessionCount);
    }

    @Test
    public void buildItemsShowsFirstSixSessionsAfterShowLess() {
        ArrayList<GhostexRemoteSession> sessions = sessionsInProject("project-a", "Ghostex", 8);
        java.util.HashSet<String> collapsedSessionListKeys = new java.util.HashSet<>();
        collapsedSessionListKeys.add("id:project-a");

        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions,
            java.util.Collections.emptySet(), collapsedSessionListKeys);

        Assert.assertEquals(8, items.size());
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_HEADER, items.get(0).type);
        Assert.assertEquals(GhostexDrawerItem.Type.SESSION, items.get(6).type);
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_SESSION_LIST_TOGGLE, items.get(7).type);
        Assert.assertTrue(items.get(7).sessionListCollapsed);
        Assert.assertEquals(GhostexDrawerItem.PROJECT_SESSION_LIST_COLLAPSED_COUNT, 6);
    }

    @Test
    public void stateCardPreservesRecoveryCopy() {
        GhostexDrawerItem item = GhostexDrawerItem.stateCard("Connection needs attention",
            "Could not reach the machine.", "Use Retry or open Tailscale.");

        Assert.assertEquals(GhostexDrawerItem.Type.STATE_CARD, item.type);
        Assert.assertEquals("Connection needs attention", item.stateTitle);
        Assert.assertEquals("Could not reach the machine.", item.stateBody);
        Assert.assertEquals("Use Retry or open Tailscale.", item.stateActionHint);
        Assert.assertNull(item.session);
    }

    @Test
    public void stateCardRowsAreEnabledForRecoveryActions() {
        ArrayList<GhostexDrawerItem> items = new ArrayList<>();
        items.add(GhostexDrawerItem.stateCard("Connection needs attention", "Review setup actions.", "Tap for setup actions."));
        GhostexRemoteSessionAdapter adapter = new GhostexRemoteSessionAdapter(RuntimeEnvironment.getApplication(), items);

        Assert.assertTrue(adapter.isEnabled(0));
    }

    @Test
    public void fromJsonAcceptsSidebarContractProviderNames() throws Exception {
        JSONObject json = new JSONObject()
            .put("alias", 7)
            .put("sessionId", "session-7")
            .put("projectId", "project-7")
            .put("projectName", "App")
            .put("primaryTitle", "Ship Android")
            .put("activity", "working")
            .put("sessionPersistenceProvider", "zmx")
            .put("sessionPersistenceName", "zmx-app")
            .put("isFocused", true);

        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);

        Assert.assertNotNull(session);
        Assert.assertEquals("7", session.alias);
        Assert.assertEquals("Ship Android", session.title);
        Assert.assertEquals("zmx", session.provider);
        Assert.assertEquals("zmx-app", session.providerSessionName);
        Assert.assertTrue(session.isFocused);
        Assert.assertTrue(session.isZmxBacked());
    }

    @Test
    public void fromJsonAcceptsLiveCliProviderNames() throws Exception {
        JSONObject json = new JSONObject()
            .put("alias", 9)
            .put("sessionId", "session-9")
            .put("projectId", "project-9")
            .put("projectName", "CLI")
            .put("title", "Live CLI fields")
            .put("status", "idle")
            .put("provider", "zmx")
            .put("providerSessionName", "zmx-cli");

        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);

        Assert.assertNotNull(session);
        Assert.assertEquals("9", session.alias);
        Assert.assertEquals("Live CLI fields", session.title);
        Assert.assertEquals("zmx", session.provider);
        Assert.assertEquals("zmx-cli", session.providerSessionName);
        Assert.assertTrue(session.isZmxBacked());
    }

    @Test
    public void fromJsonUsesMacSidebarGroupMetadataWhenProjectMetadataIsMissing() throws Exception {
        JSONObject json = new JSONObject()
            .put("alias", 10)
            .put("sessionId", "session-10")
            .put("groupId", "group-main")
            .put("groupTitle", "Main")
            .put("title", "Grouped only")
            .put("provider", "zmx");

        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);

        Assert.assertNotNull(session);
        Assert.assertEquals("group-main", session.projectId);
        Assert.assertEquals("Main", session.projectName);
        Assert.assertEquals("Main", session.displayProjectName());
    }

    @Test
    public void fromJsonKeepsSessionWithoutAlias() throws Exception {
        JSONObject json = new JSONObject()
            .put("sessionId", "session-without-alias")
            .put("projectName", "CLI")
            .put("title", "Alias omitted")
            .put("provider", "zmx");

        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);

        Assert.assertNotNull(session);
        Assert.assertEquals("session-without-alias", session.sessionId);
        Assert.assertEquals("sess", session.alias);
        Assert.assertTrue(session.isZmxBacked());
    }

    @Test
    public void fromJsonTreatsNullOptionalFieldsAsMissing() throws Exception {
        JSONObject json = new JSONObject()
            .put("alias", JSONObject.NULL)
            .put("sessionId", "session-null-fields")
            .put("projectName", JSONObject.NULL)
            .put("title", JSONObject.NULL)
            .put("provider", "zmx");

        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);

        Assert.assertNotNull(session);
        Assert.assertEquals("sess", session.alias);
        Assert.assertEquals("", session.title);
        Assert.assertEquals("Ungrouped", session.displayProjectName());
        Assert.assertTrue(session.isZmxBacked());
    }

    @Test
    public void buildItemsKeepsUnknownProjectsSessionScoped() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(new GhostexRemoteSession("1", "session-1", "", "One",
            "", "", "idle", "running", "zmx", "zmx-1", "codex",
            "2026-05-17T10:00:00Z", false, false));
        sessions.add(new GhostexRemoteSession("2", "session-2", "", "Two",
            "", "", "idle", "running", "zmx", "zmx-2", "codex",
            "2026-05-17T10:00:00Z", false, false));

        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions);

        Assert.assertEquals(4, items.size());
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_HEADER, items.get(0).type);
        Assert.assertEquals("session:session-1", items.get(0).projectKey);
        Assert.assertEquals("Ungrouped", items.get(0).projectTitle);
        Assert.assertEquals(1, items.get(0).sessionCount);
        Assert.assertEquals(GhostexDrawerItem.Type.PROJECT_HEADER, items.get(2).type);
        Assert.assertEquals("session:session-2", items.get(2).projectKey);
        Assert.assertEquals(1, items.get(2).sessionCount);
    }

    @Test
    public void fromJsonNormalizesProviderAndLifecycleTokens() throws Exception {
        JSONObject json = new JSONObject()
            .put("alias", 8)
            .put("sessionId", "session-8")
            .put("primaryTitle", "Continue work")
            .put("activity", " WORKING ")
            .put("sessionPersistenceProvider", " ZMX ")
            .put("sessionPersistenceName", "zmx-work")
            .put("status", " RUNNING ");

        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);

        Assert.assertNotNull(session);
        Assert.assertEquals("zmx", session.provider);
        Assert.assertTrue(session.isZmxBacked());
        Assert.assertEquals("working", session.displayStatus());
    }

    private GhostexRemoteSession session(String alias, String projectId, String projectName,
                                         String activity, boolean sleeping) {
        return session(alias, projectId, projectName, activity, sleeping, "2026-05-17T10:00:00Z");
    }

    private GhostexRemoteSession session(String alias, String projectId, String projectName,
                                         String activity, boolean sleeping, String lastInteractionAt) {
        return new GhostexRemoteSession(alias, "session-" + alias, projectId, "Session " + alias,
            projectName, "/tmp/" + projectName, activity, sleeping ? "sleep" : activity, "zmx",
            "zmx-" + alias, "codex", lastInteractionAt, false, sleeping);
    }

    private ArrayList<GhostexRemoteSession> sessionsInProject(String projectId, String projectName, int count) {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            sessions.add(session(String.valueOf(index), projectId, projectName, "idle", false,
                "2026-05-17T10:0" + index + ":00Z"));
        }
        return sessions;
    }

}
