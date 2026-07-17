package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GhostexWorkspaceInventory {

    /*
    CDXC:AndroidSidebar 2026-07-12-10:05:
    The mobile summary payload now mirrors the GPUI desktop sidebar: pre-sorted
    sessions with sortOrder, active project metadata, named workspace groups,
    the global agent launcher list, per-project quick actions, and explicit
    recent projects. Active projects remain visible with zero sessions; chat
    projects are marked for one synthetic Chats collection. Parse all of it in one
    holder so older CLIs that omit these fields keep the legacy Android drawer
    behavior without any feature flags.
    */
    public final List<GhostexRemoteSession> sessions;
    public final List<Project> projects;
    public final List<RecentProject> recentProjects;
    public final List<String> projectOrder;
    public final Map<String, List<SessionGroup>> groupsByProjectId;
    public final List<AgentLauncher> agents;
    public final Map<String, List<QuickAction>> quickActionsByProjectId;
    public final boolean preserveSessionOrder;

    private GhostexWorkspaceInventory(@NonNull List<GhostexRemoteSession> sessions,
                                      @NonNull List<Project> projects,
                                      @NonNull List<RecentProject> recentProjects,
                                      @NonNull List<String> projectOrder,
                                      @NonNull Map<String, List<SessionGroup>> groupsByProjectId,
                                      @NonNull List<AgentLauncher> agents,
                                      @NonNull Map<String, List<QuickAction>> quickActionsByProjectId,
                                      boolean preserveSessionOrder) {
        this.sessions = sessions;
        this.projects = projects;
        this.recentProjects = recentProjects;
        this.projectOrder = projectOrder;
        this.groupsByProjectId = groupsByProjectId;
        this.agents = agents;
        this.quickActionsByProjectId = quickActionsByProjectId;
        this.preserveSessionOrder = preserveSessionOrder;
    }

    @NonNull
    static GhostexWorkspaceInventory fromJson(@Nullable JSONObject root,
                                              @NonNull List<GhostexRemoteSession> sessions) {
        if (root == null) {
            return new GhostexWorkspaceInventory(sessions, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(),
                Collections.emptyMap(), false);
        }
        JSONObject workspaceGroups = root.optJSONObject("workspaceGroups");
        boolean preserveSessionOrder = workspaceGroups != null ||
            anySessionCarriesSortOrder(root.optJSONArray("sessions"));
        return new GhostexWorkspaceInventory(
            sessions,
            parseProjects(root.optJSONArray("projects")),
            parseRecentProjects(root.optJSONArray("recentProjects")),
            parseProjectOrder(workspaceGroups),
            parseGroupsByProjectId(workspaceGroups),
            parseAgents(root.optJSONArray("agents")),
            parseQuickActionsByProjectId(root.optJSONObject("quickActionsByProject")),
            preserveSessionOrder);
    }

    private static boolean anySessionCarriesSortOrder(@Nullable JSONArray sessionsArray) {
        if (sessionsArray == null) return false;
        for (int i = 0; i < sessionsArray.length(); i++) {
            JSONObject session = sessionsArray.optJSONObject(i);
            if (session != null && session.has("sortOrder") && !session.isNull("sortOrder")) return true;
        }
        return false;
    }

    @NonNull
    private static List<Project> parseProjects(@Nullable JSONArray array) {
        if (array == null) return Collections.emptyList();
        ArrayList<Project> projects = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            String projectId = trimmedValue(json, "projectId");
            if (projectId.isEmpty()) continue;
            String path = trimmedValue(json, "path");
            projects.add(new Project(projectId, trimmedValue(json, "name"), path,
                json.optBoolean("isChat", false) || isChatStoragePath(path)));
        }
        return projects;
    }

    private static boolean isChatStoragePath(@NonNull String path) {
        String[] segments = path.replace('\\', '/').split("/");
        for (int i = 1; i < segments.length; i++) {
            if (!"chats".equals(segments[i])) continue;
            String owner = segments[i - 1];
            if ("ghostex".equals(owner) || ".active".equals(owner) || ".ghostex".equals(owner) ||
                owner.startsWith(".ghostex-")) return true;
        }
        return false;
    }

    @NonNull
    private static List<RecentProject> parseRecentProjects(@Nullable JSONArray array) {
        if (array == null) return Collections.emptyList();
        ArrayList<RecentProject> projects = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            String projectId = trimmedValue(json, "projectId");
            if (projectId.isEmpty()) continue;
            projects.add(new RecentProject(projectId, trimmedValue(json, "title"),
                trimmedValue(json, "path"), Math.max(0, json.optInt("sessionCount", 0))));
        }
        return projects;
    }

    @NonNull
    private static List<String> parseProjectOrder(@Nullable JSONObject workspaceGroups) {
        if (workspaceGroups == null) return Collections.emptyList();
        JSONArray array = workspaceGroups.optJSONArray("projectOrder");
        if (array == null) return Collections.emptyList();
        ArrayList<String> order = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String projectId = array.isNull(i) ? "" : String.valueOf(array.opt(i)).trim();
            if (!projectId.isEmpty() && !order.contains(projectId)) order.add(projectId);
        }
        return order;
    }

    @NonNull
    private static Map<String, List<SessionGroup>> parseGroupsByProjectId(@Nullable JSONObject workspaceGroups) {
        if (workspaceGroups == null) return Collections.emptyMap();
        JSONObject projects = workspaceGroups.optJSONObject("projects");
        if (projects == null) return Collections.emptyMap();
        LinkedHashMap<String, List<SessionGroup>> byProjectId = new LinkedHashMap<>();
        for (java.util.Iterator<String> keys = projects.keys(); keys.hasNext(); ) {
            String projectId = keys.next();
            if (projectId == null || projectId.trim().isEmpty()) continue;
            JSONObject projectJson = projects.optJSONObject(projectId);
            if (projectJson == null) continue;
            JSONArray groupsArray = projectJson.optJSONArray("groups");
            if (groupsArray == null) continue;
            ArrayList<SessionGroup> groups = new ArrayList<>();
            for (int i = 0; i < groupsArray.length(); i++) {
                SessionGroup group = SessionGroup.fromJson(groupsArray.optJSONObject(i));
                if (group != null) groups.add(group);
            }
            if (!groups.isEmpty()) byProjectId.put(projectId.trim(), groups);
        }
        return byProjectId;
    }

    @NonNull
    private static List<AgentLauncher> parseAgents(@Nullable JSONArray array) {
        if (array == null) return Collections.emptyList();
        ArrayList<AgentLauncher> agents = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            String agentId = trimmedValue(json, "agentId");
            if (agentId.isEmpty()) continue;
            agents.add(new AgentLauncher(agentId, trimmedValue(json, "icon"), trimmedValue(json, "name")));
        }
        return agents;
    }

    @NonNull
    private static Map<String, List<QuickAction>> parseQuickActionsByProjectId(@Nullable JSONObject json) {
        if (json == null) return Collections.emptyMap();
        LinkedHashMap<String, List<QuickAction>> byProjectId = new LinkedHashMap<>();
        for (java.util.Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String projectId = keys.next();
            if (projectId == null || projectId.trim().isEmpty()) continue;
            JSONArray actionsArray = json.optJSONArray(projectId);
            if (actionsArray == null) continue;
            ArrayList<QuickAction> actions = new ArrayList<>();
            for (int i = 0; i < actionsArray.length(); i++) {
                QuickAction action = QuickAction.fromJson(actionsArray.optJSONObject(i));
                if (action != null) actions.add(action);
            }
            if (!actions.isEmpty()) byProjectId.put(projectId.trim(), actions);
        }
        return byProjectId;
    }

    @NonNull
    public List<SessionGroup> groupsForProject(@Nullable String projectId) {
        if (projectId == null || projectId.trim().isEmpty()) return Collections.emptyList();
        List<SessionGroup> groups = groupsByProjectId.get(projectId.trim());
        return groups == null ? Collections.emptyList() : groups;
    }

    @NonNull
    public List<QuickAction> quickActionsForProject(@Nullable String projectId) {
        if (projectId == null || projectId.trim().isEmpty()) return Collections.emptyList();
        List<QuickAction> actions = quickActionsByProjectId.get(projectId.trim());
        return actions == null ? Collections.emptyList() : actions;
    }

    @NonNull
    private static String trimmedValue(@NonNull JSONObject json, @NonNull String key) {
        if (json.isNull(key)) return "";
        Object value = json.opt(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static final class Project {
        public final String projectId;
        public final String name;
        public final String path;
        public final boolean isChat;

        Project(@NonNull String projectId, @NonNull String name, @NonNull String path,
                boolean isChat) {
            this.projectId = projectId;
            this.name = name;
            this.path = path;
            this.isChat = isChat;
        }

        @NonNull
        public String displayName() {
            if (!name.isEmpty()) return name;
            if (!path.isEmpty()) return path;
            return "Project";
        }
    }

    public static final class RecentProject {
        public final String projectId;
        public final String title;
        public final String path;
        public final int sessionCount;

        RecentProject(@NonNull String projectId, @NonNull String title,
                      @NonNull String path, int sessionCount) {
            this.projectId = projectId;
            this.title = title;
            this.path = path;
            this.sessionCount = sessionCount;
        }

        @NonNull
        public String displayTitle() {
            if (!title.isEmpty()) return title;
            if (!path.isEmpty()) return path;
            return "Project";
        }
    }

    public static final class SessionGroup {
        public final String groupId;
        public final String title;
        public final List<String> sessionIds;

        SessionGroup(@NonNull String groupId, @NonNull String title, @NonNull List<String> sessionIds) {
            this.groupId = groupId;
            this.title = title;
            this.sessionIds = sessionIds;
        }

        @Nullable
        static SessionGroup fromJson(@Nullable JSONObject json) {
            if (json == null) return null;
            String groupId = trimmedValue(json, "groupId");
            if (groupId.isEmpty()) return null;
            ArrayList<String> sessionIds = new ArrayList<>();
            JSONArray idsArray = json.optJSONArray("sessionIds");
            if (idsArray != null) {
                for (int i = 0; i < idsArray.length(); i++) {
                    String sessionId = idsArray.isNull(i) ? "" : String.valueOf(idsArray.opt(i)).trim();
                    if (!sessionId.isEmpty()) sessionIds.add(sessionId);
                }
            }
            String title = trimmedValue(json, "title");
            return new SessionGroup(groupId, title.isEmpty() ? "Group" : title, sessionIds);
        }
    }

    public static final class AgentLauncher {
        public final String agentId;
        public final String icon;
        public final String name;

        AgentLauncher(@NonNull String agentId, @NonNull String icon, @NonNull String name) {
            this.agentId = agentId;
            this.icon = icon;
            this.name = name;
        }

        @NonNull
        public String displayName() {
            return name.isEmpty() ? agentId : name;
        }
    }

    public static final class QuickAction {
        public final String actionType;
        public final String commandId;
        public final String icon;
        public final String name;
        public final String url;

        QuickAction(@NonNull String actionType, @NonNull String commandId, @NonNull String icon,
                    @NonNull String name, @NonNull String url) {
            this.actionType = actionType;
            this.commandId = commandId;
            this.icon = icon;
            this.name = name;
            this.url = url;
        }

        @Nullable
        static QuickAction fromJson(@Nullable JSONObject json) {
            if (json == null) return null;
            String actionType = trimmedValue(json, "actionType").toLowerCase(java.util.Locale.US);
            String commandId = trimmedValue(json, "commandId");
            String url = trimmedValue(json, "url");
            if ("browser".equals(actionType)) {
                if (url.isEmpty()) return null;
            } else if ("terminal".equals(actionType)) {
                if (commandId.isEmpty()) return null;
            } else {
                return null;
            }
            return new QuickAction(actionType, commandId, trimmedValue(json, "icon"),
                trimmedValue(json, "name"), url);
        }

        public boolean isBrowser() {
            return "browser".equals(actionType);
        }

        @NonNull
        public String displayName() {
            if (!name.isEmpty()) return name;
            return isBrowser() ? "Open link" : commandId;
        }
    }

}
