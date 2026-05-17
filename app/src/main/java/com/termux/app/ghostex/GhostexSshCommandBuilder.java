package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GhostexSshCommandBuilder {

    private GhostexSshCommandBuilder() {}

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-10:13:
    Android talks to the Mac by SSH and invokes the already-installed Ghostex
    CLI there. Keep command construction centralized so list, attach, and
    context-menu actions use the same host, port, quoting, and Tailscale-friendly
    host-key behavior.

    CDXC:AndroidRemoteSessions 2026-05-17-14:09:
    Android must target CLI actions by stable Ghostex session id, not by the
    visible sidebar alias. Aliases are useful labels, but can be reassigned as
    the remote sidebar list changes; session ids keep warm attach and
    long-press actions bound to the tapped row.

    CDXC:AndroidRemoteSessions 2026-05-17-12:41:
    Invoke the Mac-hosted Ghostex CLI through `/bin/zsh -lc` so SSH commands see
    the user's login-shell PATH, including Homebrew-installed `ghostex`. Keep
    the wrapper centralized so nested quoting stays consistent for list, attach,
    and context-menu actions.

    CDXC:AndroidRemoteSessions 2026-05-17-13:57:
    Remote context-menu actions should use documented `--session-id` flag forms
    instead of positional selector text. This keeps Android wake, sleep, kill,
    focus, and rename on one stable-id CLI contract.

    CDXC:AndroidRemoteSessions 2026-05-17-13:59:
    Attach is also an Android remote-session action. Use `ghostex attach
    --session-id <id>` so opening a terminal and long-press actions share the
    same documented stable-id selector contract.

    CDXC:AndroidConnectionManagement 2026-05-17-14:07:
    Check connection should prove the first-release ZMX dependency, not only SSH
    and the Ghostex CLI. Print explicit missing-tool markers so Android can show
    the existing actionable missing-CLI and missing-ZMX recovery copy.

    CDXC:AndroidConnectionManagement 2026-05-17-18:20:
    The Mac-side Ghostex CLI owns the release readiness contract for Android.
    Check connection must call `ghostex android-check --json` so it verifies
    zmx is installed, Ghostex settings are set to zmx, and the running bridge can
    return the same sidebar inventory Android will render.

    CDXC:AndroidConnectionManagement 2026-05-17-14:16:
    SSH command construction should use the same bracketed IPv6 destination that
    Settings displays and copies, keeping visible targets and executed targets
    aligned for Tailscale IPv6 machines.

    CDXC:AndroidRemoteSessions 2026-05-17-14:29:
    Cold session attach is a mobile foreground action. Use the same short SSH
    connection timeout as inventory and context actions so an offline Tailscale
    Mac does not leave the terminal stuck on SSH's default network wait.

    CDXC:AndroidRemoteSessions 2026-05-17-14:55:
    Remote action names become part of a shell command. Validate them inside
    the command builder, not only at call sites, so future Android UI changes
    cannot accidentally introduce arbitrary remote command execution.

    CDXC:AndroidRemoteSessions 2026-05-17-17:12:
    The stable session id is the command selector for attach, focus, wake,
    sleep, kill, and rename. Reject missing ids at the SSH command boundary so
    Android never sends an empty selector to the Mac-side Ghostex CLI if a
    future parser or test fixture bypasses the normal inventory validation.

    CDXC:AndroidRemoteSessions 2026-05-18-02:31:
    Android project-header creation must call the Mac-hosted Ghostex CLI rather
    than opening a local Termux shell. Pass project and group context as flags
    so the running main app creates the terminal and applies its active zmx
    persistence setting.
    */
    public static String buildSessionListCommand(@NonNull GhostexMachine machine,
                                                 boolean hasSavedPassword) {
        ArrayList<String> parts = new ArrayList<>();
        if (hasSavedPassword) parts.add("command -v sshpass >/dev/null 2>&1 && sshpass -e");
        parts.add("ssh");
        parts.add("-p " + machine.port);
        parts.add("-o ConnectTimeout=8");
        parts.add("-o StrictHostKeyChecking=accept-new");
        if (!hasSavedPassword) parts.add("-o BatchMode=yes");
        parts.add(shellQuote(machine.sshDestination()));
        parts.add(shellQuote(loginShellCommand("ghostex sessions --json")));
        return join(parts);
    }

    public static String buildAttachCommand(@NonNull GhostexMachine machine,
                                            @NonNull GhostexRemoteSession session,
                                            boolean hasSavedPassword) {
        String sessionId = requireSessionId(session);
        ArrayList<String> parts = new ArrayList<>();
        if (hasSavedPassword) parts.add("command -v sshpass >/dev/null 2>&1 && sshpass -e");
        parts.add("ssh");
        parts.add("-tt");
        parts.add("-p " + machine.port);
        parts.add("-o ConnectTimeout=8");
        parts.add("-o StrictHostKeyChecking=accept-new");
        parts.add(shellQuote(machine.sshDestination()));
        parts.add(shellQuote(loginShellCommand("ghostex attach --session-id " + shellQuote(sessionId))));
        return join(parts);
    }

    public static String buildSessionActionCommand(@NonNull GhostexMachine machine,
                                                   @NonNull GhostexRemoteSession session,
                                                   @NonNull String action,
                                                   boolean hasSavedPassword) {
        if (!isSupportedSessionAction(action)) {
            throw new IllegalArgumentException("Unsupported Ghostex session action: " + action);
        }
        String sessionId = requireSessionId(session);
        return buildRemoteGhostexCommand(machine, hasSavedPassword,
            "ghostex " + action + " --session-id " + shellQuote(sessionId) + " --json");
    }

    public static String buildRenameSessionCommand(@NonNull GhostexMachine machine,
                                                   @NonNull GhostexRemoteSession session,
                                                  @NonNull String title,
                                                  boolean hasSavedPassword) {
        String sessionId = requireSessionId(session);
        return buildRemoteGhostexCommand(machine, hasSavedPassword,
            "ghostex rename-session --session-id " + shellQuote(sessionId) +
                " --title=" + shellQuote(title) + " --json");
    }

    public static String buildCreateSessionCommand(@NonNull GhostexMachine machine,
                                                   @Nullable String projectId,
                                                   @Nullable String groupId,
                                                   boolean hasSavedPassword) {
        ArrayList<String> remoteParts = new ArrayList<>();
        remoteParts.add("ghostex create-session --json");
        String cleanProjectId = projectId == null ? "" : projectId.trim();
        String cleanGroupId = groupId == null ? "" : groupId.trim();
        if (!cleanProjectId.isEmpty()) {
            remoteParts.add("--project-id " + shellQuote(cleanProjectId));
        }
        if (!cleanGroupId.isEmpty()) {
            remoteParts.add("--group-id " + shellQuote(cleanGroupId));
        }
        return buildRemoteGhostexCommand(machine, hasSavedPassword, join(remoteParts));
    }

    public static String buildConnectionCheckCommand(@NonNull GhostexMachine machine,
                                                     boolean hasSavedPassword) {
        return buildRemoteGhostexCommand(machine, hasSavedPassword,
            "command -v ghostex >/dev/null || { printf '%s\\n' 'ghostex not found'; exit 127; }; " +
                "ghostex android-check --json");
    }

    public static String buildImageUploadCommand(@NonNull GhostexMachine machine,
                                                 boolean hasSavedPassword,
                                                 @NonNull String localPath,
                                                 @NonNull String remotePath) {
        /*
        CDXC:AndroidImageAttach 2026-05-18-01:39:
        Image attach uploads should use the same saved-machine SSH target and
        password automation as reconnect and remote actions, then paste a
        Mac-readable absolute path into the terminal as `[Image #N](path)`.
        Create the remote directory explicitly instead of relying on scp
        fallback behavior so failed uploads report the real SSH/scp error.
        */
        String mkdirCommand = buildRemoteShellCommand(machine, hasSavedPassword,
            "mkdir -p " + shellQuote(remoteDirectory(remotePath)));
        ArrayList<String> scpParts = new ArrayList<>();
        if (hasSavedPassword) scpParts.add("command -v sshpass >/dev/null 2>&1 && sshpass -e");
        scpParts.add("scp");
        scpParts.add("-q");
        scpParts.add("-P " + machine.port);
        scpParts.add("-o ConnectTimeout=8");
        scpParts.add("-o StrictHostKeyChecking=accept-new");
        if (!hasSavedPassword) scpParts.add("-o BatchMode=yes");
        scpParts.add(shellQuote(localPath));
        scpParts.add(shellQuote(machine.sshDestination() + ":" + remotePath));
        return mkdirCommand + " && " + join(scpParts);
    }

    public static String buildRemoteGhostexCommand(@NonNull GhostexMachine machine,
                                                   boolean hasSavedPassword,
                                                   @NonNull String remoteCommand) {
        return buildRemoteShellCommand(machine, hasSavedPassword, loginShellCommand(remoteCommand));
    }

    private static String buildRemoteShellCommand(@NonNull GhostexMachine machine,
                                                  boolean hasSavedPassword,
                                                  @NonNull String remoteShellCommand) {
        ArrayList<String> parts = new ArrayList<>();
        if (hasSavedPassword) parts.add("command -v sshpass >/dev/null 2>&1 && sshpass -e");
        parts.add("ssh");
        parts.add("-p " + machine.port);
        parts.add("-o ConnectTimeout=8");
        parts.add("-o StrictHostKeyChecking=accept-new");
        if (!hasSavedPassword) parts.add("-o BatchMode=yes");
        parts.add(shellQuote(machine.sshDestination()));
        parts.add(shellQuote(remoteShellCommand));
        return join(parts);
    }

    public static String shellQuote(@Nullable String value) {
        if (value == null || value.isEmpty()) return "''";
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    static String loginShellCommand(@NonNull String remoteCommand) {
        return "/bin/zsh -lc " + shellQuote(remoteCommand);
    }

    static boolean isSupportedSessionAction(@Nullable String action) {
        if (action == null) return false;
        switch (action) {
            case "focus":
            case "wake":
            case "sleep":
            case "kill":
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static String requireSessionId(@NonNull GhostexRemoteSession session) {
        String sessionId = session.sessionId == null ? "" : session.sessionId.trim();
        if (sessionId.isEmpty()) {
            throw new IllegalArgumentException("Ghostex remote session id is required.");
        }
        return session.sessionId;
    }

    private static String join(@NonNull String[] parts) {
        ArrayList<String> list = new ArrayList<>();
        for (String part : parts) list.add(part);
        return join(list);
    }

    private static String join(@NonNull List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(part);
        }
        return builder.toString();
    }

    @NonNull
    private static String remoteDirectory(@NonNull String remotePath) {
        int slashIndex = remotePath.lastIndexOf('/');
        if (slashIndex <= 0) return ".";
        return remotePath.substring(0, slashIndex);
    }

}
