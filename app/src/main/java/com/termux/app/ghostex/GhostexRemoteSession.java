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
    public final String displayTitle;
    public final String displayTitleTooltip;
    public final String projectName;
    public final String projectPath;
    public final String activity;
    public final String status;
    public final String provider;
    public final String providerSessionName;
    public final String agent;
    public final String agentIcon;
    public final String agentName;
    public final String globalRef;
    public final String kind;
    public final String lastInteractionAt;
    public final String lastActiveAt;
    public final String primaryTitle;
    public final String surface;
    public final String terminalTitle;
    public final String titleSource;
    public final String trustedResumeTitle;
    public final String updatedAt;
    public final String zmxName;
    public final boolean isFocused;
    public final boolean isFavorite;
    public final boolean isPinned;
    public final boolean isPrimaryTitleTerminalTitle;
    public final boolean isSleeping;
    public final boolean isTemporaryTitle;
    public final String nativePaneState;
    public final String providerSessionState;
    public final boolean isLive;
    public final boolean visibleInSidebarByDefault;
    public final String attentionEnteredAt;
    public final boolean attentionAcknowledged;
    public final GhostexRemoteSessionActions actions;

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

    CDXC:AndroidRemoteSessions 2026-05-29-09:20:
    Remote session liveness is resource-based. Keep `nativePaneState`,
    `providerSessionState`, and derived `isLive` beside the legacy sleep flag so
    Android shows zmx-backed sessions as live even when the Mac has no native
    pane mounted for that session.

    CDXC:AndroidRemoteSessions 2026-05-29-06:29:
    Provider-disabled Mac sessions are a known state, not an unknown backend
    check. Preserve an explicit provider-disabled value so Android can distinguish
    disabled persistence from a provider existence probe that has not completed.

    CDXC:AndroidRemoteSessions 2026-05-29-07:19:
    Normalize provider-disabled sessions to `persistence-disabled`, not generic
    `disabled`, so remote inventory names the disabled capability directly.

    CDXC:AndroidRemoteSessions 2026-06-04-03:33:
    gxserver owns presentation metadata and action eligibility. Preserve those
    CLI fields on Android rows so mobile, iOS, TUI, and agent orchestration can
    render status and decide whether to acknowledge attention without duplicating
    lifecycle, title-trust, or action-availability rules in each client.

    CDXC:GxserverSessionTitles 2026-06-07-09:33:
    Android displays gxserver's `displayTitle` while keeping raw `title` for rename and session actions. Mobile must not decide unsynced markers or placeholder title text from title provenance fields.
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
        this(alias, sessionId, projectId, groupId, title, projectName, projectPath, activity,
            status, provider, providerSessionName, agent, agentIcon, lastInteractionAt, isFocused,
            isSleeping, defaultNativePaneState(isSleeping, activity, status), "unknown",
            deriveIsLive(defaultNativePaneState(isSleeping, activity, status), "unknown", isSleeping,
                normalizedSessionState(activity), normalizedSessionState(status)));
    }

    private GhostexRemoteSession(String alias, String sessionId, String projectId, String groupId,
                                 String title, String projectName, String projectPath, String activity,
                                 String status, String provider, String providerSessionName,
                                 String agent, String agentIcon, String lastInteractionAt,
                                 boolean isFocused, boolean isSleeping, String nativePaneState,
                                 String providerSessionState, boolean isLive) {
        this(alias, sessionId, projectId, groupId, title, projectName, projectPath, activity,
            status, provider, providerSessionName, agent, agentIcon, lastInteractionAt,
            isFocused, isSleeping, nativePaneState, providerSessionState, isLive,
            title, title, "", "", "", "", "", "", "", "", "", "", "", false, false, false,
            false, false, "", false, GhostexRemoteSessionActions.empty());
    }

    private GhostexRemoteSession(String alias, String sessionId, String projectId, String groupId,
                                 String title, String projectName, String projectPath, String activity,
                                 String status, String provider, String providerSessionName,
                                 String agent, String agentIcon, String lastInteractionAt,
                                 boolean isFocused, boolean isSleeping, String nativePaneState,
                                 String providerSessionState, boolean isLive,
                                 String displayTitle, String displayTitleTooltip,
                                 String agentName, String globalRef, String kind,
                                 String lastActiveAt, String primaryTitle, String surface,
                                 String terminalTitle, String titleSource, String trustedResumeTitle,
                                 String updatedAt, String zmxName, boolean isFavorite,
                                 boolean isPinned, boolean isPrimaryTitleTerminalTitle,
                                 boolean isTemporaryTitle, boolean visibleInSidebarByDefault,
                                 String attentionEnteredAt, boolean attentionAcknowledged,
                                 GhostexRemoteSessionActions actions) {
        this.alias = alias;
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.groupId = groupId;
        this.title = title;
        this.displayTitle = displayTitle == null || displayTitle.trim().isEmpty() ? title : displayTitle.trim();
        this.displayTitleTooltip = displayTitleTooltip == null || displayTitleTooltip.trim().isEmpty()
            ? this.displayTitle : displayTitleTooltip.trim();
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.activity = activity;
        this.status = status;
        this.provider = provider;
        this.providerSessionName = providerSessionName;
        this.agent = agent;
        this.agentIcon = agentIcon;
        this.agentName = agentName;
        this.globalRef = globalRef;
        this.kind = kind;
        this.lastInteractionAt = lastInteractionAt;
        this.lastActiveAt = lastActiveAt;
        this.primaryTitle = primaryTitle;
        this.surface = surface;
        this.terminalTitle = terminalTitle;
        this.titleSource = titleSource;
        this.trustedResumeTitle = trustedResumeTitle;
        this.updatedAt = updatedAt;
        this.zmxName = zmxName;
        this.isFocused = isFocused;
        this.isFavorite = isFavorite;
        this.isPinned = isPinned;
        this.isPrimaryTitleTerminalTitle = isPrimaryTitleTerminalTitle;
        this.nativePaneState = normalizedNativePaneState(nativePaneState, isSleeping, activity, status);
        this.providerSessionState = normalizedProviderSessionState(providerSessionState);
        this.isLive = isLive;
        this.isSleeping = isSleeping && !isLive;
        this.isTemporaryTitle = isTemporaryTitle;
        this.visibleInSidebarByDefault = visibleInSidebarByDefault;
        this.attentionEnteredAt = attentionEnteredAt;
        this.attentionAcknowledged = attentionAcknowledged;
        this.actions = actions == null ? GhostexRemoteSessionActions.empty() : actions;
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
        JSONObject attention = json.optJSONObject("attention");
        boolean legacySleeping = json.optBoolean("isSleeping", "sleeping".equals(status) || "sleep".equals(status));
        String nativePaneState = normalizedNativePaneState(trimmedJsonValue(json, "nativePaneState"), legacySleeping, activity, status);
        String providerSessionState = normalizedProviderSessionState(trimmedJsonValue(json, "providerSessionState"));
        boolean isLive = json.has("isLive")
            ? json.optBoolean("isLive", false)
            : deriveIsLive(nativePaneState, providerSessionState, legacySleeping, activity, status);
        String rawTitle = firstNonEmpty(trimmedJsonValue(json, "title"), trimmedJsonValue(json, "primaryTitle"), trimmedJsonValue(json, "terminalTitle"));
        String displayTitle = firstNonEmpty(trimmedJsonValue(json, "displayTitle"), rawTitle);
        return new GhostexRemoteSession(
            alias,
            sessionId,
            firstNonEmpty(trimmedJsonValue(json, "projectId"), groupId),
            groupId,
            rawTitle,
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
            legacySleeping,
            nativePaneState,
            providerSessionState,
            isLive,
            displayTitle,
            firstNonEmpty(trimmedJsonValue(json, "displayTitleTooltip"), displayTitle),
            trimmedJsonValue(json, "agentName"),
            trimmedJsonValue(json, "globalRef"),
            trimmedJsonValue(json, "kind"),
            trimmedJsonValue(json, "lastActiveAt"),
            trimmedJsonValue(json, "primaryTitle"),
            trimmedJsonValue(json, "surface"),
            trimmedJsonValue(json, "terminalTitle"),
            trimmedJsonValue(json, "titleSource"),
            trimmedJsonValue(json, "trustedResumeTitle"),
            trimmedJsonValue(json, "updatedAt"),
            trimmedJsonValue(json, "zmxName"),
            json.optBoolean("isFavorite", false),
            json.optBoolean("isPinned", false),
            json.optBoolean("isPrimaryTitleTerminalTitle", false),
            json.optBoolean("isTemporaryTitle", false),
            json.optBoolean("visibleInSidebarByDefault", false),
            attention == null ? "" : trimmedJsonValue(attention, "enteredAt"),
            attention != null && attention.optBoolean("acknowledged", false),
            GhostexRemoteSessionActions.fromJson(json.optJSONObject("actions"))
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
        if (isSleeping && !isLive) return "sleep";
        String activityState = normalizedSessionState(activity);
        String statusState = normalizedSessionState(status);
        if (isActionableStatus(activityState)) return activityState;
        if (isActionableStatus(statusState)) return statusState;
        if (!isLive && ("sleep".equals(activityState) || "sleeping".equals(activityState) ||
            "sleep".equals(statusState) || "sleeping".equals(statusState))) return "sleep";
        if (!activityState.isEmpty() && !"running".equals(activityState) &&
            (!isLive || !"sleep".equals(activityState))) return activityState;
        if (!statusState.isEmpty() && !"running".equals(statusState) &&
            (!isLive || !"sleep".equals(statusState))) return statusState;
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

    private static String normalizedNativePaneState(String value, boolean isSleeping, String activity, String status) {
        String normalized = normalizedToken(value).replace('_', '-').replace(' ', '-');
        if ("mounted".equals(normalized) || "mounting".equals(normalized) || "unmounted".equals(normalized)) {
            return normalized;
        }
        return defaultNativePaneState(isSleeping, activity, status);
    }

    private static String defaultNativePaneState(boolean isSleeping, String activity, String status) {
        if (isSleeping) return "unmounted";
        String activityState = normalizedSessionState(activity);
        String statusState = normalizedSessionState(status);
        if ("working".equals(activityState) || "attention".equals(activityState) ||
            "working".equals(statusState) || "attention".equals(statusState) ||
            "running".equals(activityState) || "running".equals(statusState) ||
            "idle".equals(activityState) || "idle".equals(statusState)) {
            return "mounted";
        }
        return "unmounted";
    }

    private static String normalizedProviderSessionState(String value) {
        String normalized = normalizedToken(value).replace('_', '-').replace(' ', '-');
        if ("persistence-disabled".equals(normalized) || "exists".equals(normalized) ||
            "missing".equals(normalized) || "unknown".equals(normalized)) {
            return normalized;
        }
        if ("disabled".equals(normalized) || "none".equals(normalized) ||
            "off".equals(normalized) || "disabled-persistence".equals(normalized)) {
            return "persistence-disabled";
        }
        if ("running".equals(normalized)) return "exists";
        return "unknown";
    }

    private static boolean deriveIsLive(String nativePaneState, String providerSessionState,
                                        boolean isSleeping, String activity, String status) {
        if ("mounted".equals(nativePaneState) || "mounting".equals(nativePaneState) ||
            "exists".equals(providerSessionState)) {
            return true;
        }
        String activityState = normalizedSessionState(activity);
        String statusState = normalizedSessionState(status);
        if ("working".equals(activityState) || "attention".equals(activityState) ||
            "working".equals(statusState) || "attention".equals(statusState)) return true;
        if (isSleeping || "sleep".equals(activityState) || "sleep".equals(statusState) ||
            "done".equals(activityState) || "done".equals(statusState) ||
            "error".equals(activityState) || "error".equals(statusState) ||
            "exited".equals(statusState)) {
            return false;
        }
        return "running".equals(activityState) || "running".equals(statusState) ||
            "idle".equals(activityState) || "idle".equals(statusState);
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

    public static final class GhostexRemoteSessionActions {
        public final boolean acknowledgeAttention;
        public final boolean attach;
        public final boolean focus;
        public final boolean kill;
        public final boolean readText;
        public final boolean sendMessage;
        public final boolean sendText;
        public final boolean sleep;
        public final boolean wake;

        private GhostexRemoteSessionActions(boolean acknowledgeAttention, boolean attach,
                                            boolean focus, boolean kill, boolean readText,
                                            boolean sendMessage, boolean sendText,
                                            boolean sleep, boolean wake) {
            this.acknowledgeAttention = acknowledgeAttention;
            this.attach = attach;
            this.focus = focus;
            this.kill = kill;
            this.readText = readText;
            this.sendMessage = sendMessage;
            this.sendText = sendText;
            this.sleep = sleep;
            this.wake = wake;
        }

        static GhostexRemoteSessionActions empty() {
            return new GhostexRemoteSessionActions(false, false, false, false,
                false, false, false, false, false);
        }

        static GhostexRemoteSessionActions fromJson(@Nullable JSONObject json) {
            if (json == null) return empty();
            return new GhostexRemoteSessionActions(
                json.optBoolean("acknowledgeAttention", false),
                json.optBoolean("attach", false),
                json.optBoolean("focus", false),
                json.optBoolean("kill", false),
                json.optBoolean("readText", false),
                json.optBoolean("sendMessage", false),
                json.optBoolean("sendText", false),
                json.optBoolean("sleep", false),
                json.optBoolean("wake", false)
            );
        }
    }

}
