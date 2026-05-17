package com.termux.app.ghostex;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
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
    that explain the target and available tap/long-press action.

    CDXC:AndroidSidebar 2026-05-18-02:31:
    Project headers expose a plus button immediately before the session-count
    pill so Android can create a Mac-side Ghostex terminal in that group.
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

        Assert.assertEquals("Ghostex. 1 working · 2 sessions. Long press for project actions.",
            view.getContentDescription().toString());
    }

    @Test
    public void projectHeaderHasCreateButtonBeforeCountPill() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();

        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));
        TextView create = view.findViewWithTag("projectCreate");
        TextView count = view.findViewWithTag("projectCount");

        Assert.assertEquals("+", create.getText().toString());
        Assert.assertEquals("Create a session in Ghostex", create.getContentDescription().toString());
        Assert.assertTrue(((android.view.ViewGroup) view).indexOfChild(create) <
            ((android.view.ViewGroup) view).indexOfChild(count));
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
        Assert.assertTrue(description.contains("Ghostex"));
        Assert.assertTrue(description.contains("zmx-main"));
        Assert.assertTrue(description.contains("working"));
        Assert.assertTrue(description.contains("Tap to attach. Long press for actions."));
    }

    /*
    CDXC:AndroidSidebar 2026-05-17-19:42:
    Release sidebar cards should keep the macOS-inspired rounded card surface
    and status palette. A flat row fill is a visual regression because Android
    is meant to feel like the Ghostex sidebar over Termux, not the stock Termux
    local-session list.

    CDXC:AndroidSidebar 2026-05-17-20:08:
    The Android session list should keep neutral macOS sidebar chrome with
    colored status accents. Pin the current orange working state and blue focus
    accent so the drawer does not drift back into a blue-slate theme.
    */
    @Test
    public void sessionRowUsesRoundedCardAndMacSidebarStatusPalette() {
        GhostexRemoteSessionAdapter adapter = adapterForSessions();
        View view = adapter.getView(1, null, new FrameLayout(RuntimeEnvironment.getApplication()));
        TextView status = view.findViewWithTag("statusPill");

        Assert.assertTrue(view.getBackground() instanceof GradientDrawable);
        Assert.assertEquals(Color.rgb(245, 158, 11), status.getCurrentTextColor());
    }

    @Test
    public void focusedSessionUsesAccentStatusColor() {
        GhostexRemoteSession focused = new GhostexRemoteSession("3", "session-3", "project-1",
            "Focused", "Ghostex", "/Users/madda/dev/_active/zmux", "idle", "running",
            "zmx", "zmx-main", "codex", "2026-05-17T10:00:00Z", true, false);

        Assert.assertEquals(Color.rgb(125, 211, 252), GhostexRemoteSessionAdapter.statusColor(focused));
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

}
