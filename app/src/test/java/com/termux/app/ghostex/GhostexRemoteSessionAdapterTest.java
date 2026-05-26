package com.termux.app.ghostex;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class GhostexRemoteSessionAdapterTest {

    /*
    CDXC:AndroidSidebar 2026-05-17-18:38:
    The remote drawer is the primary Android navigation surface. Its clickable
    state, project, and session rows need composed accessibility descriptions
    that explain the target and available tap, overflow, or long-press action.

    CDXC:AndroidSidebar 2026-05-18-02:31:
    Project headers expose a plus button beside the project title so Android
    can create a Mac-side Ghostex terminal in that group.

    CDXC:AndroidSidebar 2026-05-19-10:15:
    Session rows are single-line title cards without alias badges or metadata.

    CDXC:AndroidSidebar 2026-05-18-05:18:
    Project headers expose a three-dot action button to the right of the plus
    button because project menus should be visible and tap-driven instead of
    depending on long-pressing the project name.

    CDXC:AndroidSidebar 2026-05-18-06:51:
    The project actions button should use the vertical-dots icon drawable, not
    a literal `...` label, so tests pin the control type and drawable presence.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Project headers are disclosure rows. The title and plus controls must share
    one exact 32dp height while the vertical-dots icon is visually smaller
    inside the same button height.

    CDXC:AndroidSidebar 2026-05-23-14:40:
    Mobile session cards need a visible status dot so the Android app shows
    working state when the macOS desktop status indicator does.

    CDXC:AndroidSidebar 2026-05-26-10:14:
    Status indicators are circular views and actionable status beats idle
    activity, so tests pin both the visible row affordance and the inventory
    normalization that keeps working/attention state from disappearing.
    */
    @Test
    public void stateCardHasRecoveryContentDescription() {
        ArrayList<GhostexDrawerItem> items = new ArrayList<>();
        items.add(GhostexDrawerItem.stateCard("Connection needs attention",
            "Could not reach the machine.", "Use Retry or open Tailscale."));
        GhostexRemoteSessionAdapter adapter =
            new GhostexRemoteSessionAdapter(RuntimeEnvironment.getApplication(), items);

        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        Assert.assertEquals("Connection needs attention. Could not reach the machine. Use Retry or open Tailscale.",
            view.getContentDescription().toString());
    }

    @Test
    public void projectHeaderHasActionContentDescription() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();

        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        Assert.assertEquals("Ghostex. Tap to collapse. Use plus to create a session. Use more for project actions.",
            view.getContentDescription().toString());
    }

    @Test
    public void projectHeaderHasCreateAndActionsButtonsInOrder() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();

        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));
        TextView create = view.findViewWithTag("projectCreate");
        ImageButton actions = view.findViewWithTag("projectActions");

        Assert.assertNull(view.findViewWithTag("projectCount"));
        Assert.assertEquals("+", create.getText().toString());
        Assert.assertNotNull(actions.getDrawable());
        Assert.assertEquals("Create a session in Ghostex", create.getContentDescription().toString());
        Assert.assertEquals("Open project actions for Ghostex", actions.getContentDescription().toString());
        Assert.assertEquals(dp(32), create.getLayoutParams().height);
        Assert.assertEquals(dp(32), actions.getLayoutParams().height);
        Assert.assertEquals(dp(10), actions.getPaddingLeft());
        Assert.assertTrue(((android.view.ViewGroup) view).indexOfChild(create) <
            ((android.view.ViewGroup) view).indexOfChild(actions));
    }

    @Test
    public void projectHeaderCreateButtonInvokesListener() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();
        final GhostexDrawerItem[] clickedItem = new GhostexDrawerItem[1];
        adapter.setOnProjectSessionCreateListener(item -> clickedItem[0] = item);
        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        view.findViewWithTag("projectCreate").performClick();

        Assert.assertNotNull(clickedItem[0]);
        Assert.assertEquals("project-1", clickedItem[0].projectId);
    }

    @Test
    public void projectHeaderActionsButtonInvokesListener() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();
        final GhostexDrawerItem[] clickedItem = new GhostexDrawerItem[1];
        adapter.setOnProjectActionsListener(item -> clickedItem[0] = item);
        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        view.findViewWithTag("projectActions").performClick();

        Assert.assertNotNull(clickedItem[0]);
        Assert.assertEquals("project-1", clickedItem[0].projectId);
    }

    @Test
    public void projectHeaderClickInvokesToggleListener() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();
        final GhostexDrawerItem[] clickedItem = new GhostexDrawerItem[1];
        adapter.setOnProjectToggleListener(item -> clickedItem[0] = item);
        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        view.performClick();

        Assert.assertNotNull(clickedItem[0]);
        Assert.assertEquals("project-1", clickedItem[0].projectId);
    }

    @Test
    public void projectHeaderCarriesRemoteGroupIdForCreateSession() throws Exception {
        JSONObject json = new JSONObject()
            .put("alias", 4)
            .put("sessionId", "session-4")
            .put("projectId", "project-4")
            .put("groupId", "group-main")
            .put("projectName", "Ghostex")
            .put("provider", "zmx");

        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);
        Assert.assertNotNull(session);
        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(java.util.Collections.singletonList(session));

        Assert.assertEquals("project-4", items.get(0).projectId);
        Assert.assertEquals("group-main", items.get(0).groupId);
    }

    @Test
    public void sessionRowHasAttachAndActionsContentDescription() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();

        View view = adapter.getView(1, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        String description = view.getContentDescription().toString();
        Assert.assertTrue(description.contains("Session 1"));
        Assert.assertFalse(description.contains("zmx-main"));
        Assert.assertTrue(description.contains("Tap to attach. Long press for actions."));
    }

    /*
    CDXC:AndroidSidebar 2026-05-17-19:42:
    Release sidebar cards should keep the macOS-inspired rounded card surface.
    A flat row fill is a visual regression because Android is meant to feel like
    the Ghostex sidebar over Termux, not the stock Termux local-session list.
    */
    @Test
    public void sessionRowUsesRoundedCardWithoutMetadataRow() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();
        View view = adapter.getView(1, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        Assert.assertTrue(view.getBackground() instanceof GradientDrawable);
        Assert.assertNull(view.findViewWithTag("badge"));
        Assert.assertNull(view.findViewWithTag("meta"));
        Assert.assertNull(view.findViewWithTag("statusPill"));
        Assert.assertNotNull(view.findViewWithTag("title"));
        Assert.assertNotNull(view.findViewWithTag("agentIcon"));
        Assert.assertNotNull(view.findViewWithTag("statusDot"));
        Assert.assertNotNull(view.findViewWithTag("sleepingIcon"));
    }

    @Test
    public void sessionRowShowsWorkingStatusDot() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();
        View view = adapter.getView(1, null, new FrameLayout(RuntimeEnvironment.getApplication()));
        TextView statusDot = view.findViewWithTag("statusDot");

        Assert.assertEquals("", statusDot.getText().toString());
        Assert.assertTrue(statusDot.getBackground() instanceof GradientDrawable);
        Assert.assertEquals(GhostexPalette.STATUS_WORKING, statusDot.getCurrentTextColor());
        Assert.assertTrue(view.getContentDescription().toString().contains("Working."));
    }

    @Test
    public void sleepingSessionRowShowsSleepIconOnRight() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(new GhostexRemoteSession("1", "session-1", "project-1", "Sleeping",
            "Ghostex", "/Users/madda/dev/_active/zmux", "idle", "sleep", "zmx",
            "zmx-main", "codex", "2026-05-17T10:00:00Z", false, true));
        GhostexRemoteSessionAdapter adapter = new GhostexRemoteSessionAdapter(
            RuntimeEnvironment.getApplication(), GhostexDrawerItem.buildItems(sessions));

        View view = adapter.getView(1, null, new FrameLayout(RuntimeEnvironment.getApplication()));
        TextView statusDot = view.findViewWithTag("statusDot");
        View sleepingIcon = view.findViewWithTag("sleepingIcon");

        Assert.assertTrue(GhostexRemoteSessionAdapter.showsSleepingIcon(sessions.get(0)));
        Assert.assertEquals(View.GONE, statusDot.getVisibility());
        Assert.assertEquals(View.VISIBLE, sleepingIcon.getVisibility());
        Assert.assertTrue(view.getContentDescription().toString().contains("Sleeping."));
    }

    @Test
    public void projectSessionListToggleUsesShowMoreShowLessLabels() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            sessions.add(new GhostexRemoteSession(String.valueOf(index), "session-" + index,
                "project-1", "Session " + index, "Ghostex", "/Users/madda/dev/_active/zmux",
                "idle", "running", "zmx", "zmx-main", "codex",
                "2026-05-17T10:0" + index + ":00Z", false, false));
        }
        GhostexRemoteSessionAdapter expandedAdapter = new GhostexRemoteSessionAdapter(
            RuntimeEnvironment.getApplication(), GhostexDrawerItem.buildItems(sessions));

        TextView expandedToggle = (TextView) expandedAdapter.getView(9, null,
            new FrameLayout(RuntimeEnvironment.getApplication()));
        Assert.assertEquals("Show less", expandedToggle.getText().toString());

        java.util.HashSet<String> collapsedLists = new java.util.HashSet<>();
        collapsedLists.add("id:project-1");
        GhostexRemoteSessionAdapter collapsedAdapter = new GhostexRemoteSessionAdapter(
            RuntimeEnvironment.getApplication(), GhostexDrawerItem.buildItems(sessions,
                java.util.Collections.emptySet(), collapsedLists));

        TextView collapsedToggle = (TextView) collapsedAdapter.getView(7, null,
            new FrameLayout(RuntimeEnvironment.getApplication()));
        Assert.assertEquals("Show more", collapsedToggle.getText().toString());
    }

    @Test
    public void actionableStatusOverridesIdleActivityForStatusDot() throws Exception {
        JSONObject json = new JSONObject()
            .put("alias", 5)
            .put("sessionId", "session-5")
            .put("title", "Needs input")
            .put("activity", "idle")
            .put("status", "attention")
            .put("provider", "zmx");
        GhostexRemoteSession session = GhostexRemoteSession.fromJson(json);

        Assert.assertNotNull(session);
        Assert.assertEquals("attention", session.displayStatus());
        Assert.assertEquals(GhostexPalette.STATUS_ATTENTION, GhostexRemoteSessionAdapter.statusColor(session));
    }

    @Test
    public void focusedSessionUsesAccentStatusColor() {
        GhostexRemoteSession focused = new GhostexRemoteSession("3", "session-3", "project-1",
            "Focused", "Ghostex", "/Users/madda/dev/_active/zmux", "idle", "running",
            "zmx", "zmx-main", "codex", "2026-05-17T10:00:00Z", true, false);

        Assert.assertEquals(Color.rgb(125, 211, 252), GhostexRemoteSessionAdapter.statusColor(focused));
    }

    @Test
    public void workingStatusColorOverridesFocusedAccent() {
        GhostexRemoteSession focusedWorking = new GhostexRemoteSession("4", "session-4", "project-1",
            "Focused working", "Ghostex", "/Users/madda/dev/_active/zmux", "working", "running",
            "zmx", "zmx-main", "codex", "2026-05-17T10:00:00Z", true, false);

        Assert.assertEquals(GhostexPalette.STATUS_WORKING, GhostexRemoteSessionAdapter.statusColor(focusedWorking));
    }

    private GhostexRemoteSessionAdapter adapterForSessions() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(new GhostexRemoteSession("1", "session-1", "project-1", "Session 1",
            "Ghostex", "/Users/madda/dev/_active/zmux", "working", "running", "zmx",
            "zmx-main", "codex", "2026-05-17T10:00:00Z", false, false));
        sessions.add(new GhostexRemoteSession("2", "session-2", "project-1", "Session 2",
            "Ghostex", "/Users/madda/dev/_active/zmux", "idle", "running", "zmx",
            "zmx-main", "codex", "2026-05-17T09:00:00Z", false, false));
        List<GhostexDrawerItem> items = GhostexDrawerItem.buildItems(sessions);
        return new GhostexRemoteSessionAdapter(RuntimeEnvironment.getApplication(), items);
    }

    private int dp(int value) {
        return Math.round(value * RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density);
    }

}
