package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class GhostexDrawerItem {

    public static final int PROJECT_SESSION_LIST_COLLAPSED_COUNT = 6;

    public enum Type {
        STATE_CARD,
        MACHINE_HEADER,
        PROJECT_HEADER,
        PROJECT_AGENTS_ROW,
        PROJECT_EMPTY,
        GROUP_HEADER,
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
    public final boolean isChatCollection;
    public final GhostexRemoteSession session;
    public final String groupTitle;
    public final String groupCollapseKey;
    public final List<GhostexWorkspaceInventory.AgentLauncher> agents;
    public final List<GhostexWorkspaceInventory.QuickAction> quickActions;
    @NonNull private String machineId = "";

    private GhostexDrawerItem(@NonNull Type type, @NonNull String stateTitle,
                              @NonNull String stateBody, @NonNull String stateActionHint,
                              @NonNull String projectKey,
                              @NonNull String projectId, @NonNull String groupId,
                              @NonNull String projectTitle, @NonNull String projectPath,
                              int sessionCount, int workingCount, int attentionCount,
                              int sleepingCount, boolean collapsed, boolean sessionListCollapsed,
                              @Nullable GhostexRemoteSession session) {
        this(type, stateTitle, stateBody, stateActionHint, projectKey, projectId, groupId,
            projectTitle, projectPath, sessionCount, workingCount, attentionCount, sleepingCount,
            collapsed, sessionListCollapsed, session, "", "",
            Collections.emptyList(), Collections.emptyList());
    }

    private GhostexDrawerItem(@NonNull Type type, @NonNull String stateTitle,
                              @NonNull String stateBody, @NonNull String stateActionHint,
                              @NonNull String projectKey,
                              @NonNull String projectId, @NonNull String groupId,
                              @NonNull String projectTitle, @NonNull String projectPath,
                              int sessionCount, int workingCount, int attentionCount,
                              int sleepingCount, boolean collapsed, boolean sessionListCollapsed,
                              @Nullable GhostexRemoteSession session,
                              @NonNull String groupTitle, @NonNull String groupCollapseKey,
                              @NonNull List<GhostexWorkspaceInventory.AgentLauncher> agents,
                              @NonNull List<GhostexWorkspaceInventory.QuickAction> quickActions) {
        this(type, stateTitle, stateBody, stateActionHint, projectKey, projectId, groupId,
            projectTitle, projectPath, sessionCount, workingCount, attentionCount, sleepingCount,
            collapsed, sessionListCollapsed, session, groupTitle, groupCollapseKey, agents,
            quickActions, false);
    }

    private GhostexDrawerItem(@NonNull Type type, @NonNull String stateTitle,
                              @NonNull String stateBody, @NonNull String stateActionHint,
                              @NonNull String projectKey,
                              @NonNull String projectId, @NonNull String groupId,
                              @NonNull String projectTitle, @NonNull String projectPath,
                              int sessionCount, int workingCount, int attentionCount,
                              int sleepingCount, boolean collapsed, boolean sessionListCollapsed,
                              @Nullable GhostexRemoteSession session,
                              @NonNull String groupTitle, @NonNull String groupCollapseKey,
                              @NonNull List<GhostexWorkspaceInventory.AgentLauncher> agents,
                              @NonNull List<GhostexWorkspaceInventory.QuickAction> quickActions,
                              boolean isChatCollection) {
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
        this.isChatCollection = isChatCollection;
        this.session = session;
        this.groupTitle = groupTitle;
        this.groupCollapseKey = groupCollapseKey;
        this.agents = agents;
        this.quickActions = quickActions;
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

    CDXC:AndroidSidebar 2026-07-12-10:05:
    The mobile summary is now pre-sorted to match the GPUI desktop sidebar and
    carries the full workspace shape. When the payload signals desktop ordering
    (workspaceGroups or per-session sortOrder), Android must preserve the array
    order instead of re-sorting, preserve every active project even when its
    final session closes, combine chat storage projects into one Chats section,
    order normal projects by workspaceGroups.projectOrder, and split sessions into the
    implicit main group followed by the named GPUI groups in their persisted
    sessionIds order. Explicit recent projects never enter this list. Named groups collapse per
    projectKey+groupId like project disclosure. Old payloads without these
    fields keep the legacy sorting and layout exactly as before.

    CDXC:AndroidSidebar 2026-07-12-10:05:
    The agents isle and quick actions render as one horizontally scrollable
    chip row under the project header. It only appears when the summary payload
    exposes agents or per-project quick actions and the project has a stable
    projectId, because create-agent and run-action are project-scoped CLI calls.
    */
    public static GhostexDrawerItem stateCard(@NonNull String title, @NonNull String body,
                                              @NonNull String actionHint) {
        return new GhostexDrawerItem(Type.STATE_CARD, title, body, actionHint,
            "", "", "", "", "", 0, 0, 0, 0, false, false, null);
    }

    /*
    CDXC:AndroidSidebar 2026-07-18:
    The drawer now stacks every saved machine as its own section, mirroring the
    desktop sidebar's per-remote-machine sections. Machine headers are
    collapsible disclosure rows above that machine's Quick/project items, and
    every row carries its owning machine id so attach, create, and context
    actions target the right Mac even when two machines expose identical
    session or project ids.
    */
    public static GhostexDrawerItem machineHeader(@NonNull String machineId,
                                                  @NonNull String machineTitle,
                                                  boolean collapsed) {
        GhostexDrawerItem item = new GhostexDrawerItem(Type.MACHINE_HEADER, "", "", "",
            "machine:" + machineId, "", "", machineTitle, "", 0, 0, 0, 0, collapsed, false, null);
        item.machineId = machineId;
        return item;
    }

    @NonNull
    public String machineId() {
        return machineId;
    }

    @NonNull
    public static List<GhostexDrawerItem> stampMachineId(@NonNull List<GhostexDrawerItem> items,
                                                         @NonNull String machineId) {
        for (GhostexDrawerItem item : items) item.machineId = machineId;
        return items;
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
        return buildItems(sessions, null, collapsedProjectKeys, collapsedProjectSessionListKeys,
            Collections.emptySet());
    }

    public static List<GhostexDrawerItem> buildItems(@NonNull List<GhostexRemoteSession> sessions,
                                                     @Nullable GhostexWorkspaceInventory workspace,
                                                     @NonNull Set<String> collapsedProjectKeys,
                                                     @NonNull Set<String> collapsedProjectSessionListKeys,
                                                     @NonNull Set<String> collapsedGroupKeys) {
        LinkedHashMap<String, ArrayList<GhostexRemoteSession>> sessionsByProjectKey = new LinkedHashMap<>();
        HashMap<String, GhostexWorkspaceInventory.Project> projectById = new HashMap<>();
        if (workspace != null) {
            for (GhostexWorkspaceInventory.Project project : workspace.projects) {
                projectById.put(project.projectId, project);
            }
        }
        for (GhostexRemoteSession session : sessions) {
            GhostexWorkspaceInventory.Project project = projectById.get(session.projectId);
            String key = project != null && project.isChat ? "chats" : groupKey(session);
            ArrayList<GhostexRemoteSession> projectSessions = sessionsByProjectKey.get(key);
            if (projectSessions == null) {
                projectSessions = new ArrayList<>();
                sessionsByProjectKey.put(key, projectSessions);
            }
            projectSessions.add(session);
        }
        if (workspace != null) {
            for (GhostexWorkspaceInventory.Project project : workspace.projects) {
                String key = project.isChat ? "chats" : "id:" + project.projectId;
                if (!sessionsByProjectKey.containsKey(key)) {
                    sessionsByProjectKey.put(key, new ArrayList<>());
                }
            }
        }

        boolean preserveSessionOrder = workspace != null && workspace.preserveSessionOrder;
        if (!preserveSessionOrder) {
            for (ArrayList<GhostexRemoteSession> projectSessions : sessionsByProjectKey.values()) {
                Collections.sort(projectSessions, GhostexSessionCardFormatter::compareForSidebarOrder);
            }
        }

        ArrayList<String> orderedProjectKeys = new ArrayList<>();
        if (workspace != null) {
            if (sessionsByProjectKey.containsKey("chats")) orderedProjectKeys.add("chats");
            for (String projectId : workspace.projectOrder) {
                String key = "id:" + projectId;
                if (sessionsByProjectKey.containsKey(key) && !orderedProjectKeys.contains(key)) {
                    orderedProjectKeys.add(key);
                }
            }
            for (GhostexWorkspaceInventory.Project project : workspace.projects) {
                String key = project.isChat ? "chats" : "id:" + project.projectId;
                if (sessionsByProjectKey.containsKey(key) && !orderedProjectKeys.contains(key)) {
                    orderedProjectKeys.add(key);
                }
            }
        }
        for (String key : sessionsByProjectKey.keySet()) {
            if (!orderedProjectKeys.contains(key)) orderedProjectKeys.add(key);
        }

        ArrayList<GhostexDrawerItem> items = new ArrayList<>();
        for (String key : orderedProjectKeys) {
            ArrayList<GhostexRemoteSession> projectSessions = sessionsByProjectKey.get(key);
            if (projectSessions == null) continue;
            boolean isChatCollection = "chats".equals(key);
            GhostexRemoteSession first = projectSessions.isEmpty() ? null : projectSessions.get(0);
            GhostexWorkspaceInventory.Project project = null;
            if (!isChatCollection && workspace != null && key.startsWith("id:")) {
                project = projectById.get(key.substring(3));
            }
            String projectId = isChatCollection ? ""
                : project != null ? project.projectId : first == null ? "" : first.projectId;
            String projectTitle = isChatCollection ? "Chats"
                : project != null ? project.displayName() : first == null ? "Project" : first.displayProjectName();
            String projectPath = isChatCollection ? ""
                : project != null ? project.path : first == null ? "" : first.projectPath;
            String legacyGroupId = first == null ? "" : first.groupId;
            int working = 0;
            int attention = 0;
            int sleeping = 0;
            for (GhostexRemoteSession session : projectSessions) {
                String status = session.displayStatus();
                if ("working".equals(status)) working++;
                if ("attention".equals(status)) attention++;
                if ("sleep".equals(status) || "sleeping".equals(status)) sleeping++;
            }
            boolean collapsed = collapsedProjectKeys.contains(key);
            items.add(new GhostexDrawerItem(Type.PROJECT_HEADER, "", "", "", key,
                projectId, legacyGroupId, projectTitle, projectPath,
                projectSessions.size(), working, attention, sleeping, collapsed, false, null,
                "", "", Collections.emptyList(), Collections.emptyList(), isChatCollection));
            if (collapsed) continue;

            List<GhostexWorkspaceInventory.AgentLauncher> agents = workspace == null
                ? Collections.emptyList() : workspace.agents;
            List<GhostexWorkspaceInventory.QuickAction> quickActions = workspace == null
                ? Collections.<GhostexWorkspaceInventory.QuickAction>emptyList()
                : workspace.quickActionsForProject(projectId);
            if (!isChatCollection && !projectId.isEmpty() && (!agents.isEmpty() || !quickActions.isEmpty())) {
                items.add(new GhostexDrawerItem(Type.PROJECT_AGENTS_ROW, "", "", "", key,
                    projectId, legacyGroupId, projectTitle, projectPath,
                    0, 0, 0, 0, false, false, null, "", "", agents, quickActions));
            }

            if (projectSessions.isEmpty()) {
                items.add(new GhostexDrawerItem(Type.PROJECT_EMPTY, "", "", "", key,
                    projectId, legacyGroupId, projectTitle, projectPath,
                    0, 0, 0, 0, false, false, null));
                continue;
            }

            List<GhostexWorkspaceInventory.SessionGroup> namedGroups = workspace == null || isChatCollection
                ? Collections.<GhostexWorkspaceInventory.SessionGroup>emptyList()
                : workspace.groupsForProject(projectId);
            if (namedGroups.isEmpty()) {
                addFlatProjectSessionItems(items, key, projectId, legacyGroupId, projectTitle,
                    projectPath, projectSessions, collapsedProjectSessionListKeys);
                continue;
            }
            addGroupedProjectSessionItems(items, key, projectId, legacyGroupId, projectTitle,
                projectPath, projectSessions, namedGroups, collapsedGroupKeys);
        }
        return items;
    }

    private static void addFlatProjectSessionItems(@NonNull ArrayList<GhostexDrawerItem> items,
                                                   @NonNull String key, @NonNull String projectId,
                                                   @NonNull String legacyGroupId,
                                                   @NonNull String projectTitle,
                                                   @NonNull String projectPath,
                                                   @NonNull ArrayList<GhostexRemoteSession> projectSessions,
                                                   @NonNull Set<String> collapsedProjectSessionListKeys) {
        boolean sessionListCollapsed = collapsedProjectSessionListKeys.contains(key) &&
            projectSessions.size() > PROJECT_SESSION_LIST_COLLAPSED_COUNT;
        int visibleSessionCount = sessionListCollapsed
            ? PROJECT_SESSION_LIST_COLLAPSED_COUNT
            : projectSessions.size();
        for (int index = 0; index < visibleSessionCount; index++) {
            GhostexRemoteSession session = projectSessions.get(index);
            items.add(new GhostexDrawerItem(Type.SESSION, "", "", "", key,
                projectId, legacyGroupId, projectTitle, projectPath,
                0, 0, 0, 0, false, false, session));
        }
        if (projectSessions.size() > PROJECT_SESSION_LIST_COLLAPSED_COUNT) {
            items.add(new GhostexDrawerItem(Type.PROJECT_SESSION_LIST_TOGGLE, "", "", "", key,
                projectId, legacyGroupId, projectTitle, projectPath,
                projectSessions.size(), 0, 0, 0, false, sessionListCollapsed, null));
        }
    }

    private static void addGroupedProjectSessionItems(@NonNull ArrayList<GhostexDrawerItem> items,
                                                      @NonNull String key, @NonNull String projectId,
                                                      @NonNull String legacyGroupId,
                                                      @NonNull String projectTitle,
                                                      @NonNull String projectPath,
                                                      @NonNull ArrayList<GhostexRemoteSession> projectSessions,
                                                      @NonNull List<GhostexWorkspaceInventory.SessionGroup> namedGroups,
                                                      @NonNull Set<String> collapsedGroupKeys) {
        HashMap<String, GhostexRemoteSession> sessionsById = new HashMap<>();
        for (GhostexRemoteSession session : projectSessions) {
            sessionsById.put(session.sessionId, session);
        }
        HashSet<String> claimedSessionIds = new HashSet<>();
        for (GhostexWorkspaceInventory.SessionGroup group : namedGroups) {
            claimedSessionIds.addAll(group.sessionIds);
        }
        for (GhostexRemoteSession session : projectSessions) {
            if (claimedSessionIds.contains(session.sessionId)) continue;
            items.add(new GhostexDrawerItem(Type.SESSION, "", "", "", key,
                projectId, legacyGroupId, projectTitle, projectPath,
                0, 0, 0, 0, false, false, session));
        }
        for (GhostexWorkspaceInventory.SessionGroup group : namedGroups) {
            ArrayList<GhostexRemoteSession> groupSessions = new ArrayList<>();
            for (String sessionId : group.sessionIds) {
                GhostexRemoteSession session = sessionsById.get(sessionId);
                if (session != null) groupSessions.add(session);
            }
            if (groupSessions.isEmpty()) continue;
            String collapseKey = groupCollapseKey(key, group.groupId);
            boolean groupCollapsed = collapsedGroupKeys.contains(collapseKey);
            items.add(new GhostexDrawerItem(Type.GROUP_HEADER, "", "", "", key,
                projectId, group.groupId, projectTitle, projectPath,
                groupSessions.size(), 0, 0, 0, groupCollapsed, false, null,
                group.title, collapseKey, Collections.emptyList(), Collections.emptyList()));
            if (groupCollapsed) continue;
            for (GhostexRemoteSession session : groupSessions) {
                items.add(new GhostexDrawerItem(Type.SESSION, "", "", "", key,
                    projectId, group.groupId, projectTitle, projectPath,
                    0, 0, 0, 0, false, false, session));
            }
        }
    }

    static String groupCollapseKey(@NonNull String projectKey, @NonNull String groupId) {
        return projectKey + "|" + groupId;
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

}
