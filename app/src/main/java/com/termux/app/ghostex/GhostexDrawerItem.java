package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public final class GhostexDrawerItem {

    public enum Type {
        STATE_CARD,
        PROJECT_HEADER,
        SESSION
    }

    public final Type type;
    public final String stateTitle;
    public final String stateBody;
    public final String stateActionHint;
    public final String projectKey;
    public final String projectId;
    public final String groupId;
    public final String projectTitle;
    public final String projectPath;
    public final int sessionCount;
    public final int workingCount;
    public final int attentionCount;
    public final int sleepingCount;
    public final GhostexRemoteSession session;

    private GhostexDrawerItem(@NonNull Type type, @NonNull String stateTitle,
                              @NonNull String stateBody, @NonNull String stateActionHint,
                              @NonNull String projectKey,
                              @NonNull String projectId, @NonNull String groupId,
                              @NonNull String projectTitle, @NonNull String projectPath,
                              int sessionCount, int workingCount, int attentionCount,
                              int sleepingCount, @Nullable GhostexRemoteSession session) {
        this.type = type;
        this.stateTitle = stateTitle;
        this.stateBody = stateBody;
        this.stateActionHint = stateActionHint;
        this.projectKey = projectKey;
        this.projectId = projectId;
        this.groupId = groupId;
        this.projectTitle = projectTitle;
        this.projectPath = projectPath;
        this.sessionCount = sessionCount;
        this.workingCount = workingCount;
        this.attentionCount = attentionCount;
        this.sleepingCount = sleepingCount;
        this.session = session;
    }

    /*
    CDXC:AndroidSidebar 2026-05-17-10:43:
    Ghostex Android must preserve the macOS sidebar structure on touch devices.
    Build project header rows from the remote CLI inventory so project-level
    context menus and section summaries exist without changing Termux's core
    session list controller.

    CDXC:AndroidConnectionManagement 2026-05-17-13:02:
    Failed and empty reconnect states need visible recovery cards in the drawer,
    not only status text. State rows keep the machine dropdown, Retry, Setup,
    Add, and Tailscale controls contextual while the session list area explains
    what to do next.

    CDXC:AndroidSidebar 2026-05-17-12:45:
    Project groups should enforce the same attention, working, then Last Active
    ordering as the macOS sidebar even if the remote CLI payload changes order.
    This makes Android switching predictable across reconnects and refreshes.

    CDXC:AndroidSidebar 2026-05-17-18:01:
    When the CLI omits project id, path, and name, do not merge every unknown
    project into one synthetic group. Fall back to the stable session id so
    project-level context actions stay scoped to the row the user can inspect.

    CDXC:AndroidSidebar 2026-05-17-21:09:
    Project rows should sort by the most important visible session inside each
    group, matching the macOS sidebar's attention, working, then Last Active
    cues. Do not leave project order to the remote JSON's incidental sequence.
    */
    public static GhostexDrawerItem stateCard(@NonNull String title, @NonNull String body,
                                              @NonNull String actionHint) {
        return new GhostexDrawerItem(Type.STATE_CARD, title, body, actionHint,
            "", "", "", "", "", 0, 0, 0, 0, null);
    }

    public static List<GhostexDrawerItem> buildItems(@NonNull List<GhostexRemoteSession> sessions) {
        LinkedHashMap<String, ArrayList<GhostexRemoteSession>> groups = new LinkedHashMap<>();
        for (GhostexRemoteSession session : sessions) {
            String key = groupKey(session);
            ArrayList<GhostexRemoteSession> groupSessions = groups.get(key);
            if (groupSessions == null) {
                groupSessions = new ArrayList<>();
                groups.put(key, groupSessions);
            }
            groupSessions.add(session);
        }

        ArrayList<Group> sortedGroups = new ArrayList<>();
        for (String key : groups.keySet()) {
            ArrayList<GhostexRemoteSession> groupSessions = groups.get(key);
            if (groupSessions == null || groupSessions.isEmpty()) continue;
            Collections.sort(groupSessions, GhostexSessionCardFormatter::compareForSidebarOrder);
            sortedGroups.add(new Group(key, groupSessions));
        }
        Collections.sort(sortedGroups, (left, right) ->
            GhostexSessionCardFormatter.compareForSidebarOrder(left.firstSession(), right.firstSession()));

        ArrayList<GhostexDrawerItem> items = new ArrayList<>();
        for (Group group : sortedGroups) {
            ArrayList<GhostexRemoteSession> groupSessions = group.sessions;
            GhostexRemoteSession first = group.firstSession();
            int working = 0;
            int attention = 0;
            int sleeping = 0;
            for (GhostexRemoteSession session : groupSessions) {
                String status = session.displayStatus();
                if ("working".equals(status)) working++;
                if ("attention".equals(status)) attention++;
                if ("sleep".equals(status) || "sleeping".equals(status)) sleeping++;
            }
            items.add(new GhostexDrawerItem(Type.PROJECT_HEADER, "", "", "", group.key,
                first.projectId, first.groupId, first.displayProjectName(), first.projectPath,
                groupSessions.size(), working, attention, sleeping, null));
            for (GhostexRemoteSession session : groupSessions) {
                items.add(new GhostexDrawerItem(Type.SESSION, "", "", "", group.key,
                    first.projectId, first.groupId, first.displayProjectName(), first.projectPath,
                    0, 0, 0, 0, session));
            }
        }
        return items;
    }

    private static String groupKey(@NonNull GhostexRemoteSession session) {
        if (!session.projectId.isEmpty()) return "id:" + session.projectId;
        if (!session.projectPath.isEmpty()) return "path:" + session.projectPath;
        if (!session.projectName.isEmpty()) return "name:" + session.projectName;
        return "session:" + session.sessionId;
    }

    private static final class Group {
        final String key;
        final ArrayList<GhostexRemoteSession> sessions;

        Group(@NonNull String key, @NonNull ArrayList<GhostexRemoteSession> sessions) {
            this.key = key;
            this.sessions = sessions;
        }

        @NonNull
        GhostexRemoteSession firstSession() {
            return sessions.get(0);
        }
    }

}
