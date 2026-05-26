package com.termux.app.ghostex;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Locale;

public final class GhostexRemoteSession {

    public final String alias;
    public final String sessionId;
    public final String projectId;
    public final String groupId;
    public final String title;
    public final String projectName;
    public final String projectPath;
    public final String activity;
    public final String status;
    public final String provider;
    public final String providerSessionName;
    public final String agent;
    public final String agentIcon;
    public final String lastInteractionAt;
    public final boolean isFocused;
    public final boolean isSleeping;

    /*
    CDXC:AndroidSidebar 2026-05-17-10:43:
    Android sidebar rows mirror the macOS combined sidebar, so remote sessions
    carry project grouping, lifecycle, focus, and provider metadata instead of
    only the fields needed to launch an SSH attach command.

    CDXC:AndroidRemoteSessions 2026-05-17-12:52:
    Provider and lifecycle values are protocol-like fields from the Mac CLI.
    Normalize them before filtering so harmless case differences or whitespace
    do not hide valid ZMX sessions from the Android drawer.

    CDXC:AndroidRemoteSessions 2026-05-17-15:33:
    Android remote commands depend on `sessionId`; `alias` is display-only.
    If the Mac CLI ever omits alias for a valid session, keep the row usable by
    deriving a compact badge from the stable session id instead of dropping the
    session from the Android drawer.

    CDXC:AndroidRemoteSessions 2026-05-17-17:49:
    Treat JSON nulls from the Mac CLI as missing fields, not as visible "null"
    strings. The Android drawer should derive display-only aliases from stable
    session ids and leave optional titles/project metadata blank when the CLI
    omits them.

    CDXC:AndroidSidebar 2026-05-17-18:01:
    Sessions without project metadata should not collapse into one shared
    project group. Use a neutral "Ungrouped" label for display while the drawer
    groups those rows by stable session id so project-level destructive actions
    cannot target unrelated sessions from malformed or sparse CLI payloads.

    CDXC:AndroidSidebar 2026-05-17-20:03:
    The Mac bridge can expose sidebar grouping as `groupId` and `groupTitle`
    when project metadata is sparse. Use those as fallback Android project
    metadata so the drawer still mirrors the macOS sidebar grouping instead of
    demoting valid grouped rows to per-session Ungrouped buckets.

    CDXC:AndroidSidebar 2026-05-19-11:05:
    The Mac inventory can expose `agentIcon` directly. Keep it alongside the
    human-readable agent label so Android session cards can render the same
    logo identity as the macOS sidebar without re-deriving icons in the adapter.

    CDXC:AndroidSidebar 2026-05-26-10:14:
    Android session rows must keep working and attention indicators visible even
    when the Mac inventory sends `activity: idle` plus an actionable lifecycle
    `status`. Prefer actionable status tokens over idle activity so mobile
    mirrors the desktop sidebar instead of hiding active-session state.
    */
    public GhostexRemoteSession(String alias, String sessionId, String projectId, String title,
                                String projectName, String projectPath, String activity,
                                String status, String provider, String providerSessionName,
                                String agent, String lastInteractionAt, boolean isFocused,
                                boolean isSleeping) {
        this(alias, sessionId, projectId, "", title, projectName, projectPath, activity,
            status, provider, providerSessionName, agent, "", lastInteractionAt, isFocused,
            isSleeping);
    }

    public GhostexRemoteSession(String alias, String sessionId, String projectId, String groupId,
                                String title, String projectName, String projectPath, String activity,
                                String status, String provider, String providerSessionName,
                                String agent, String lastInteractionAt, boolean isFocused,
                                boolean isSleeping) {
        this(alias, sessionId, projectId, groupId, title, projectName, projectPath, activity,
            status, provider, providerSessionName, agent, "", lastInteractionAt, isFocused,
            isSleeping);
    }

    public GhostexRemoteSession(String alias, String sessionId, String projectId, String groupId,
                                String title, String projectName, String projectPath, String activity,
                                String status, String provider, String providerSessionName,
                                String agent, String agentIcon, String lastInteractionAt,
                                boolean isFocused, boolean isSleeping) {
        this.alias = alias;
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.groupId = groupId;
        this.title = title;
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.activity = activity;
        this.status = status;
        this.provider = provider;
        this.providerSessionName = providerSessionName;
        this.agent = agent;
        this.agentIcon = agentIcon;
        this.lastInteractionAt = lastInteractionAt;
        this.isFocused = isFocused;
        this.isSleeping = isSleeping;
    }

    @Nullable
    public static GhostexRemoteSession fromJson(JSONObject json) {
        if (json == null) return null;
        String sessionId = trimmedJsonValue(json, "sessionId");
        String alias = trimmedJsonValue(json, "alias");
        if (sessionId.isEmpty()) return null;
        if (alias.isEmpty()) alias = aliasFromSessionId(sessionId);
        String provider = normalizedToken(firstNonEmpty(trimmedJsonValue(json, "provider"), trimmedJsonValue(json, "sessionPersistenceProvider")));
        String providerSessionName = firstNonEmpty(trimmedJsonValue(json, "providerSessionName"), trimmedJsonValue(json, "sessionPersistenceName"));
        String status = normalizedSessionState(firstNonEmpty(trimmedJsonValue(json, "status"), trimmedJsonValue(json, "lifecycleState")));
        String activity = normalizedSessionState(firstNonEmpty(trimmedJsonValue(json, "activity"),
            trimmedJsonValue(json, "activityState"), trimmedJsonValue(json, "activityStatus")));
        String groupId = trimmedJsonValue(json, "groupId");
        return new GhostexRemoteSession(
            alias,
            sessionId,
            firstNonEmpty(trimmedJsonValue(json, "projectId"), groupId),
            groupId,
            firstNonEmpty(trimmedJsonValue(json, "title"), trimmedJsonValue(json, "primaryTitle"), trimmedJsonValue(json, "terminalTitle")),
            firstNonEmpty(trimmedJsonValue(json, "projectName"), trimmedJsonValue(json, "groupTitle")),
            trimmedJsonValue(json, "projectPath"),
            activity,
            status,
            provider,
            providerSessionName,
            trimmedJsonValue(json, "agent"),
            trimmedJsonValue(json, "agentIcon"),
            trimmedJsonValue(json, "lastInteractionAt"),
            json.optBoolean("isFocused", false),
            json.optBoolean("isSleeping", "sleeping".equals(status) || "sleep".equals(status))
        );
    }

    public boolean isZmxBacked() {
        return "zmx".equals(normalizedToken(provider));
    }

    public String displayProjectName() {
        if (!projectName.isEmpty()) return projectName;
        if (!projectPath.isEmpty()) return projectPath;
        return "Ungrouped";
    }

    public String displayStatus() {
        if (isSleeping) return "sleep";
        String activityState = normalizedSessionState(activity);
        String statusState = normalizedSessionState(status);
        if (isActionableStatus(activityState)) return activityState;
        if (isActionableStatus(statusState)) return statusState;
        if ("sleep".equals(activityState) || "sleeping".equals(activityState) ||
            "sleep".equals(statusState) || "sleeping".equals(statusState)) return "sleep";
        if (!activityState.isEmpty() && !"running".equals(activityState)) return activityState;
        if (!statusState.isEmpty() && !"running".equals(statusState)) return statusState;
        return "idle";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String normalizedToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String normalizedSessionState(String value) {
        String normalized = normalizedToken(value).replace('_', '-').replace(' ', '-');
        if ("needs-attention".equals(normalized) || "attention-required".equals(normalized)) return "attention";
        if ("active".equals(normalized) || "busy".equals(normalized) || "processing".equals(normalized)) return "working";
        if ("sleeping".equals(normalized)) return "sleep";
        return normalized;
    }

    private static boolean isActionableStatus(String value) {
        return "attention".equals(value) || "working".equals(value) ||
            "done".equals(value) || "error".equals(value);
    }

    private static String trimmedJsonValue(JSONObject json, String key) {
        if (json.isNull(key)) return "";
        Object value = json.opt(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String aliasFromSessionId(String sessionId) {
        return sessionId.length() <= 4 ? sessionId : sessionId.substring(0, 4);
    }

}
