package com.termux.app.ghostex;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GhostexSessionInventoryClient {

    private final GhostexSshTransport sshTransport;

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-10:13:
    The sidebar inventory must come from `ghostex sessions --json`, not from
    scraping terminal text. Filter to ZMX-backed sessions here so the Android
    drawer only offers workflows supported by the first release.

    CDXC:AndroidRemoteSessions 2026-05-17-14:09:
    Remote actions use the stable `sessionId` returned by the CLI JSON list.
    The visible alias can change between refreshes, so Android should not rely
    on alias-cache side effects for wake, sleep, or kill.

    CDXC:AndroidSidebar 2026-05-17-14:34:
    Android session context menus should cover the macOS focus and rename
    actions when the Ghostex CLI exposes them. Use stable session ids and
    structured `--title=` arguments so rename stays safe for titles with spaces.

    CDXC:AndroidRemoteSessions 2026-05-17-18:24:
    Inventory and remote action commands are non-interactive, but they still
    depend on the Mac-hosted Ghostex CLI contract. Use the app-owned SSHJ
    transport directly so reconnect behaves like attach without requiring
    phone-side Termux package binaries.

    CDXC:AndroidConnectionRecovery 2026-05-17-12:32:
    SSHJ and the remote Ghostex CLI usually report actionable failures. Merge
    stderr into captured command output so failed reconnect cards and password
    recovery prompts can show the real reason instead of a generic empty-output
    message.

    CDXC:AndroidRemoteSessions 2026-05-17-12:38:
    Remote SSH login shells may print banners, warnings, or profile text around
    `ghostex sessions --json`. Parse only the first complete JSON object so the
    Android drawer remains tied to machine-readable CLI output without being
    brittle to harmless shell framing.

    CDXC:AndroidRemoteSessions 2026-05-17-13:42:
    Shell banners can contain their own brace-delimited text before the Ghostex
    JSON. Treat the Android contract as "the first complete JSON object with a
    sessions array" so decorative MOTD/profile output cannot mask the real CLI
    payload.

    CDXC:AndroidRemoteSessions 2026-05-17-14:32:
    Some login shells print unmatched brace snippets before command output.
    Keep scanning for later complete JSON objects so a harmless banner cannot
    hide the real Ghostex CLI payload or JSON failure object.

    CDXC:AndroidConnectionRecovery 2026-05-17-13:00:
    Release recovery copy should map common SSH and remote Ghostex CLI failures
    to next actions. Keep host-key mismatch, refused SSH, DNS/Tailscale reach,
    missing remote CLI, and missing zmx distinct so users can repair the right
    side of the connection without reading raw terminal stderr.

        CDXC:AndroidConnectionManagement 2026-05-17-14:03:
        Saved-machine management needs a lightweight readiness check separate from
        full session reconnect. Reuse the same SSH command path to validate the
        Mac, Ghostex CLI, and sessions endpoint without changing the visible drawer.

        CDXC:AndroidConnectionManagement 2026-05-17-18:20:
        The readiness check is now an explicit Mac-side CLI contract. Android
        should surface missing `ghostex android-check`, non-zmx persistence, and
        missing zmx as setup problems instead of treating them like a generic
        empty session list.

        CDXC:AndroidConnectionRecovery 2026-05-17-19:07:
        Timeout results bypass stderr summarization, so route them through
        GhostexRemoteTimeoutCopy instead of keeping one-off strings in each
        SSH path. This keeps reconnect and drawer action recovery aligned.

        CDXC:AndroidRemoteSessions 2026-05-18-02:31:
        Creating a session from Android is a Mac-side Ghostex CLI action. Keep
        it on the same SSH/process path as focus, rename, and lifecycle actions
        so the main app owns terminal creation and zmx persistence selection.

        CDXC:AndroidSshTransport 2026-05-18-02:56:
        Non-interactive remote CLI calls should use the app-owned SSH transport
        instead of Termux package binaries so reconnect, readiness, create, and
        context actions remain compatible with modern target SDKs.

        CDXC:AndroidSidebar 2026-05-18-16:13:
        Project move commands are desktop-sidebar mutations, not Android-local
        sorting preferences. Send them through the Mac Ghostex CLI and refresh
        inventory afterward so mobile mirrors the persisted desktop order.
    */
    public GhostexSessionInventoryClient(@NonNull Context context) {
        sshTransport = new GhostexSshTransport(context);
    }


    public static final class Result {
        public final boolean ok;
        public final String errorMessage;
        public final List<GhostexRemoteSession> sessions;
        @Nullable public final String createdSessionId;

        private Result(boolean ok, @Nullable String errorMessage,
                       @NonNull List<GhostexRemoteSession> sessions,
                       @Nullable String createdSessionId) {
            this.ok = ok;
            this.errorMessage = errorMessage;
            this.sessions = sessions;
            this.createdSessionId = createdSessionId;
        }

        public static Result success(@NonNull List<GhostexRemoteSession> sessions) {
            return new Result(true, null, sessions, null);
        }

        public static Result createSuccess(@Nullable String createdSessionId) {
            return new Result(true, null, new ArrayList<>(), createdSessionId);
        }

        public static Result failure(@NonNull String errorMessage) {
            return new Result(false, errorMessage, new ArrayList<>(), null);
        }
    }

    public Result fetchSessions(@NonNull GhostexMachine machine, @Nullable String password) {
        try {
            GhostexSshTransport.CommandResult commandResult = runRemoteGhostexCommand(machine, password,
                "ghostex sessions --json", "zmx=none op=fetchSessions");
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.connecting(machine));
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(summarizeFailure(commandResult.output, hasPassword(password)));
            }
            return Result.success(parseSessions(commandResult.output));
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not load Ghostex sessions." : error.getMessage());
        }
    }

    public Result runSessionAction(@NonNull GhostexMachine machine, @Nullable String password,
                                   @NonNull String action, @NonNull GhostexRemoteSession session) {
        try {
            GhostexSshTransport.CommandResult commandResult = runRemoteGhostexCommand(machine, password,
                GhostexSshCommandBuilder.sessionActionRemoteCommand(session, action),
                "zmx=" + session.alias + " sessionId=" + session.sessionId + " op=" + action);
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.sessionAction(action));
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(summarizeFailure(commandResult.output, hasPassword(password)));
            }
            return Result.success(new ArrayList<>());
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not run ghostex " + action + "." : error.getMessage());
        }
    }

    public Result renameSession(@NonNull GhostexMachine machine, @Nullable String password,
                                @NonNull GhostexRemoteSession session, @NonNull String title) {
        try {
            GhostexSshTransport.CommandResult commandResult = runRemoteGhostexCommand(machine, password,
                GhostexSshCommandBuilder.renameSessionRemoteCommand(session, title),
                "zmx=" + session.alias + " sessionId=" + session.sessionId + " op=rename");
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.renaming());
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(summarizeFailure(commandResult.output, hasPassword(password)));
            }
            return Result.success(new ArrayList<>());
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not rename this session." : error.getMessage());
        }
    }

    public Result createSession(@NonNull GhostexMachine machine, @Nullable String password,
                                @NonNull GhostexDrawerItem projectItem) {
        try {
            GhostexSshTransport.CommandResult commandResult = runRemoteGhostexCommand(machine, password,
                GhostexSshCommandBuilder.createSessionRemoteCommand(projectItem.projectId, projectItem.groupId),
                "zmx=none op=createSession");
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.sessionAction("create session"));
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(summarizeFailure(commandResult.output, hasPassword(password)));
            }
            return Result.createSuccess(parseCreatedSessionId(commandResult.output));
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not create a Ghostex session." : error.getMessage());
        }
    }

    /*
    CDXC:AndroidRemoteSessions 2026-05-19-11:20:
    `ghostex create-session --json` returns a compact created-session summary.
    Parse that stable session id so Android can refresh inventory and attach the
    new terminal immediately instead of leaving the drawer open for manual attach.
    */
    @Nullable
    static String parseCreatedSessionId(@NonNull String output) {
        try {
            JSONObject root = new JSONObject(extractFirstJsonObject(output));
            if (!root.optBoolean("ok", false)) return null;
            JSONObject session = root.optJSONObject("session");
            if (session == null) return null;
            String sessionId = session.optString("sessionId", "").trim();
            return sessionId.isEmpty() ? null : sessionId;
        } catch (Exception ignored) {
            return null;
        }
    }

    public Result moveProject(@NonNull GhostexMachine machine, @Nullable String password,
                              @NonNull GhostexDrawerItem projectItem,
                              @NonNull String direction) {
        try {
            GhostexSshTransport.CommandResult commandResult = runRemoteGhostexCommand(machine, password,
                GhostexSshCommandBuilder.moveProjectRemoteCommand(projectItem.projectId, direction),
                "zmx=none op=moveProject direction=" + direction);
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.sessionAction("move project"));
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(summarizeFailure(commandResult.output, hasPassword(password)));
            }
            return Result.success(new ArrayList<>());
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not move this project." : error.getMessage());
        }
    }

    public Result checkConnection(@NonNull GhostexMachine machine, @Nullable String password) {
        try {
            GhostexSshTransport.CommandResult commandResult = runRemoteGhostexCommand(machine, password,
                "command -v ghostex >/dev/null || { printf '%s\\n' 'ghostex not found'; exit 127; }; " +
                    "ghostex android-check --json", "zmx=none op=checkConnection");
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.checking(machine));
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(summarizeFailure(commandResult.output, hasPassword(password)));
            }
            return Result.success(new ArrayList<>());
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not check this machine." : error.getMessage());
        }
    }

    static List<GhostexRemoteSession> parseSessions(@NonNull String output) throws Exception {
        JSONObject root = extractSessionListJsonObject(output);
        JSONArray array = root.optJSONArray("sessions");
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        if (array == null) return sessions;
        for (int i = 0; i < array.length(); i++) {
            GhostexRemoteSession session = GhostexRemoteSession.fromJson(array.optJSONObject(i));
            if (session != null && session.isZmxBacked()) sessions.add(session);
        }
        return sessions;
    }

    static JSONObject extractSessionListJsonObject(@NonNull String output) throws Exception {
        int searchStart = 0;
        while (searchStart < output.length()) {
            String jsonText = extractFirstJsonObject(output, searchStart);
            if (jsonText == null) break;
            try {
                JSONObject candidate = new JSONObject(jsonText);
                if (candidate.optJSONArray("sessions") != null) return candidate;
            } catch (Exception ignored) {
                // Keep scanning; profile output may contain non-JSON brace blocks.
            }
            searchStart = output.indexOf(jsonText, searchStart) + jsonText.length();
            if (searchStart <= 0) break;
        }
        throw new Exception(output.indexOf('{') < 0
            ? "Ghostex CLI did not return JSON."
            : "Ghostex CLI did not return a sessions JSON payload.");
    }

    static String extractFirstJsonObject(@NonNull String output) throws Exception {
        String jsonText = extractFirstJsonObject(output, 0);
        if (jsonText == null) throw new Exception("Ghostex CLI did not return JSON.");
        return jsonText;
    }

    @Nullable
    private static String extractFirstJsonObject(@NonNull String output, int fromIndex) throws Exception {
        int searchStart = Math.max(0, fromIndex);
        boolean sawIncompleteObject = false;
        while (searchStart < output.length()) {
            int start = output.indexOf('{', searchStart);
            if (start < 0) break;
            String candidate = extractJsonObjectAt(output, start);
            if (candidate != null) return candidate;
            sawIncompleteObject = true;
            searchStart = start + 1;
        }
        if (sawIncompleteObject) throw new Exception("Ghostex CLI returned incomplete JSON.");
        return null;
    }

    @Nullable
    private static String extractJsonObjectAt(@NonNull String output, int start) {
        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        for (int index = start; index < output.length(); index++) {
            char value = output.charAt(index);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (value == '\\') {
                    escaping = true;
                } else if (value == '"') {
                    inString = false;
                }
                continue;
            }
            if (value == '"') {
                inString = true;
            } else if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) return output.substring(start, index + 1);
            }
        }
        return null;
    }

    private GhostexSshTransport.CommandResult runRemoteGhostexCommand(@NonNull GhostexMachine machine,
                                                                      @Nullable String password,
                                                                      @NonNull String remoteCommand) {
        return runRemoteGhostexCommand(machine, password, remoteCommand, null);
    }

    private GhostexSshTransport.CommandResult runRemoteGhostexCommand(@NonNull GhostexMachine machine,
                                                                      @Nullable String password,
                                                                      @NonNull String remoteCommand,
                                                                      @Nullable String logTag) {
        return sshTransport.exec(machine, password, GhostexSshCommandBuilder.loginShellCommand(remoteCommand), logTag);
    }

    private static boolean hasPassword(@Nullable String password) {
        return password != null && !password.isEmpty();
    }

    static String summarizeFailure(@Nullable String output, boolean usedPassword) {
        String text = output == null ? "" : output.trim();
        /*
        CDXC:AndroidConnectionRecovery 2026-05-17-14:25:
        Android-facing Ghostex CLI actions now exit nonzero when the macOS
        bridge reports `{ ok: false }`. Prefer the JSON error field over raw
        payload text so drawer recovery and password prompts remain readable
        after focus, rename, or lifecycle action failures.

        CDXC:AndroidConnectionRecovery 2026-05-17-14:32:
        Failed CLI JSON may also follow shell/profile snippets with unmatched
        braces. Use the shared resilient JSON scanner before falling back to
        plain stderr text.
        */
        String cliError = extractCliError(text);
        if (cliError != null) text = cliError;
        /*
        CDXC:AndroidConnectionRecovery 2026-05-17-20:05:
        Failure matching is protocol-style ASCII text from SSH, Ghostex, and zmx.
        Use Locale.ROOT so devices in Turkish or other locale-specific casing
        environments do not miss CLI repair hints.
        */
        String lowerText = text.toLowerCase(Locale.ROOT);
        if (text.contains("Host key verification failed") || text.contains("REMOTE HOST IDENTIFICATION HAS CHANGED")) {
            return "SSH host key verification failed. Open Setup and reset this phone's saved host key for the machine, or confirm you are connecting to the right Mac.";
        }
        if (text.contains("Permission denied")) {
            return usedPassword
                ? "SSH rejected the saved password. Update the saved machine password or choose another machine."
                : "SSH needs a key or password. Open the machine settings and save a password, or configure SSH keys/Tailscale SSH.";
        }
        if (text.contains("Connection refused")) {
            return "The machine is reachable, but SSH refused the connection. Enable Remote Login on the Mac and confirm the saved SSH port.";
        }
        if (text.contains("Could not resolve") || text.contains("Name or service not known") ||
            text.contains("No address associated with hostname") || text.contains("No route to host") ||
            text.contains("Connection timed out") || text.contains("Operation timed out")) {
            return "Could not reach the machine. Open Tailscale and confirm both devices are online.";
        }
        if (lowerText.contains("ghostex: command not found") || lowerText.contains("command not found: ghostex") ||
            lowerText.contains("ghostex not found")) {
            return "Connected over SSH, but the Mac could not find the Ghostex CLI. Install Ghostex CLI and make sure `ghostex` is available in the SSH login shell.";
        }
        if (lowerText.contains("unknown command: android-check")) {
            return "Connected over SSH, but this Mac has an older Ghostex CLI. Update Ghostex so `ghostex android-check --json` is available.";
        }
        if (lowerText.contains("session persistence is set to") &&
            !lowerText.contains("session persistence is set to zmx")) {
            return "Ghostex is reachable, but Session persistence is not set to zmx. Open Ghostex Settings on the Mac and set Session persistence to zmx.";
        }
        if (lowerText.contains("zmx") && (lowerText.contains("not found") || lowerText.contains("not configured"))) {
            return "Ghostex is reachable, but ZMX is not available on the Mac. Install zmx and set Ghostex session persistence to zmx.";
        }
        if (text.isEmpty()) return "Could not connect to the machine.";
        return text.length() > 220 ? text.substring(0, 220) + "..." : text;
    }

    @Nullable
    private static String extractCliError(@NonNull String output) {
        int searchStart = 0;
        while (searchStart < output.length()) {
            String jsonText;
            try {
                jsonText = extractFirstJsonObject(output, searchStart);
            } catch (Exception ignored) {
                return null;
            }
            if (jsonText == null) return null;
            try {
                JSONObject candidate = new JSONObject(jsonText);
                if (!candidate.optBoolean("ok", true) ||
                    !candidate.optBoolean("bridgeOk", true)) {
                    String message = firstNonEmpty(candidate.optString("error", ""),
                        candidate.optString("message", ""));
                    if (!message.isEmpty()) return message;
                }
            } catch (Exception ignored) {
                // Continue scanning; shell/profile text can contain brace blocks.
            }
            searchStart = output.indexOf(jsonText, searchStart) + jsonText.length();
            if (searchStart <= 0) return null;
        }
        return null;
    }

    private static String firstNonEmpty(@NonNull String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

}
