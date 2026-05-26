package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class GhostexDrawerItem {

    public static final int PROJECT_SESSION_LIST_COLLAPSED_COUNT = 6;

    public enum Type {
        STATE_CARD,
        PROJECT_HEADER,
        PROJECT_SESSION_LIST_TOGGLE,
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
    public final boolean collapsed;
    public final boolean sessionListCollapsed;
    public final GhostexRemoteSession session;

    private GhostexDrawerItem(@NonNull Type type, @NonNull String stateTitle,
                              @NonNull String stateBody, @NonNull String stateActionHint,
                              @NonNull String projectKey,
                              @NonNull String projectId, @NonNull String groupId,
                              @NonNull String projectTitle, @NonNull String projectPath,
                              int sessionCount, int workingCount, int attentionCount,
                              int sleepingCount, boolean collapsed, boolean sessionListCollapsed,
                              @Nullable GhostexRemoteSession session) {
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
        this.collapsed = collapsed;
        this.sessionListCollapsed = sessionListCollapsed;
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

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Project order is owned by the desktop Ghostex sidebar and reaches Android
    through the CLI inventory. Preserve that group order on mobile while still
    sorting sessions inside each project by attention, working, and recency.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Project headers are tappable disclosure rows on Android. Keep collapse state
    keyed by the same project key used for project actions so refreshes preserve
    the user's expanded/collapsed view without changing the Mac-side ordering.

    CDXC:AndroidRemoteSessions 2026-05-19-11:20:
    After Android creates a session in a project, resolve the created row by
    matching the same project key used for project headers instead of guessing
    from unrelated sessions in the inventory payload.
    */
    public static GhostexDrawerItem stateCard(@NonNull String title, @NonNull String body,
                                              @NonNull String actionHint) {
        return new GhostexDrawerItem(Type.STATE_CARD, title, body, actionHint,
            "", "", "", "", "", 0, 0, 0, 0, false, false, null);
    }

    public static List<GhostexDrawerItem> buildItems(@NonNull List<GhostexRemoteSession> sessions) {
        return buildItems(sessions, Collections.emptySet());
    }

    public static List<GhostexDrawerItem> buildItems(@NonNull List<GhostexRemoteSession> sessions,
                                                     @NonNull Set<String> collapsedProjectKeys) {
        return buildItems(sessions, collapsedProjectKeys, Collections.emptySet());
    }

    public static List<GhostexDrawerItem> buildItems(@NonNull List<GhostexRemoteSession> sessions,
                                                     @NonNull Set<String> collapsedProjectKeys,
                                                     @NonNull Set<String> collapsedProjectSessionListKeys) {
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
            boolean collapsed = collapsedProjectKeys.contains(group.key);
            items.add(new GhostexDrawerItem(Type.PROJECT_HEADER, "", "", "", group.key,
                first.projectId, first.groupId, first.displayProjectName(), first.projectPath,
                groupSessions.size(), working, attention, sleeping, collapsed, false, null));
            if (collapsed) continue;
            boolean sessionListCollapsed = collapsedProjectSessionListKeys.contains(group.key) &&
                groupSessions.size() > PROJECT_SESSION_LIST_COLLAPSED_COUNT;
            int visibleSessionCount = sessionListCollapsed
                ? PROJECT_SESSION_LIST_COLLAPSED_COUNT
                : groupSessions.size();
            for (int index = 0; index < visibleSessionCount; index++) {
                GhostexRemoteSession session = groupSessions.get(index);
                items.add(new GhostexDrawerItem(Type.SESSION, "", "", "", group.key,
                    first.projectId, first.groupId, first.displayProjectName(), first.projectPath,
                    0, 0, 0, 0, false, false, session));
            }
            if (groupSessions.size() > PROJECT_SESSION_LIST_COLLAPSED_COUNT) {
                items.add(new GhostexDrawerItem(Type.PROJECT_SESSION_LIST_TOGGLE, "", "", "", group.key,
                    first.projectId, first.groupId, first.displayProjectName(), first.projectPath,
                    groupSessions.size(), 0, 0, 0, false, sessionListCollapsed, null));
            }
        }
        return items;
    }

    static String projectKeyForSession(@NonNull GhostexRemoteSession session) {
        return groupKey(session);
    }

    boolean containsSession(@NonNull GhostexRemoteSession session) {
        return projectKey.equals(projectKeyForSession(session));
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
