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
	    zmx is installed, Ghostex settings are set to zmx, and gxserver can return
	    the same sidebar inventory Android will render without the macOS app.

	    CDXC:AndroidRemoteSessions 2026-06-11-23:52:
	    Session inventory and status commands still run through SSH on the Mac, but
	    their data source is gxserver. Do not route list/status through a macOS app
	    bridge or a sidebar persistence fallback.

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

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Android project reordering must be a Mac-side Ghostex CLI action so the
    desktop app persists the same sidebar order that mobile later receives from
    `ghostex sessions --json`. Validate direction tokens before shell assembly.

    CDXC:AndroidRemoteSessions 2026-06-04-03:33:
    Attention acknowledgement is a gxserver-owned presentation action exposed
    through the Mac-side CLI. Keep it in the same validated session-action path
    as focus, wake, sleep, and kill so Android taps clear attention without
    inventing a separate status mutation.
    */
    public static String buildCopyableAttachCommand(@NonNull GhostexMachine machine,
                                                    @NonNull GhostexRemoteSession session) {
        /*
        CDXC:AndroidRemoteAttach 2026-05-18-06:51:
        Copy attach command is a human support affordance, not the Android
        transport path. Keep it as plain `ssh` syntax without password-helper
        dependencies while the app itself uses SSHJ for phone-side networking.
        */
        ArrayList<String> parts = new ArrayList<>();
        parts.add("ssh");
        parts.add("-tt");
        parts.add("-p " + machine.port);
        parts.add("-o StrictHostKeyChecking=accept-new");
        parts.add(shellQuote(machine.sshDestination()));
        parts.add(shellQuote(loginShellCommand(attachRemoteCommand(session))));
        return join(parts);
    }

    public static String buildSessionActionCommand(@NonNull GhostexMachine machine,
                                                   @NonNull GhostexRemoteSession session,
                                                   @NonNull String action,
                                                   boolean hasSavedPassword) {
        return sessionActionRemoteCommand(session, action);
    }

    public static String buildRenameSessionCommand(@NonNull GhostexMachine machine,
                                                  @NonNull GhostexRemoteSession session,
                                                  @NonNull String title,
                                                  boolean hasSavedPassword) {
        return renameSessionRemoteCommand(session, title);
    }

    public static String buildCreateSessionCommand(@NonNull GhostexMachine machine,
                                                   @Nullable String projectId,
                                                   @Nullable String groupId,
                                                   boolean hasSavedPassword) {
        return createSessionRemoteCommand(projectId, groupId);
    }

    public static String sessionActionRemoteCommand(@NonNull GhostexRemoteSession session,
                                                    @NonNull String action) {
        if (!isSupportedSessionAction(action)) {
            throw new IllegalArgumentException("Unsupported Ghostex session action: " + action);
        }
        String sessionId = requireSessionId(session);
        /*
        CDXC:AndroidRemoteSessions 2026-05-31-08:45:
        gxserver lifecycle RPCs are project-scoped. Android already receives
        projectId from `ghostex sessions --json`, so include it on sleep, wake,
        focus, and kill commands instead of relying on a Mac-side selector
        lookup when the tapped row has enough identity.
        */
        String projectId = session.projectId == null ? "" : session.projectId.trim();
        String projectFlag = projectId.isEmpty() ? "" : " --project-id " + shellQuote(projectId);
        return "ghostex " + action + " --session-id " + shellQuote(sessionId) + projectFlag + " --json";
    }

    public static String attachRemoteCommand(@NonNull GhostexRemoteSession session) {
        /*
        CDXC:AndroidRemoteAttach 2026-05-18-04:51:
        The app-owned SSH attach path and the copyable support command must
        resolve to the same Mac-side CLI invocation. Keep the remote command
        separate from any local shell wrapper so Android can open a PTY through
        SSHJ without depending on Termux package binaries.

        CDXC:AndroidRemoteAttach 2026-06-04-02:22:
        G session ids are project-scoped in gxserver. Include projectId on
        attach, matching lifecycle and rename actions, so the Mac-side CLI
        resolves the exact full server/project/session zmx route instead of a
        bare session id that can collide across projects.
        */
        String projectId = session.projectId == null ? "" : session.projectId.trim();
        String projectFlag = projectId.isEmpty() ? "" : " --project-id " + shellQuote(projectId);
        return "ghostex attach --session-id " + shellQuote(requireSessionId(session)) + projectFlag;
    }

    public static String renameSessionRemoteCommand(@NonNull GhostexRemoteSession session,
                                                    @NonNull String title) {
        String sessionId = requireSessionId(session);
        String projectId = session.projectId == null ? "" : session.projectId.trim();
        String projectFlag = projectId.isEmpty() ? "" : " --project-id " + shellQuote(projectId);
        return "ghostex rename-session --session-id " + shellQuote(sessionId) +
            projectFlag +
            " --title=" + shellQuote(title) + " --json";
    }

    public static String createSessionRemoteCommand(@Nullable String projectId,
                                                    @Nullable String groupId) {
        /*
        CDXC:AndroidSshTransport 2026-05-18-02:56:
        App-owned SSH exec reuses the same Mac-side command strings that the
        shell-based prototype used, keeping action quoting in one place while
        the transport stays independent of Termux package binaries.
        */
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
        return join(remoteParts);
    }

    public static String moveProjectRemoteCommand(@Nullable String projectId,
                                                  @NonNull String direction) {
        String cleanProjectId = projectId == null ? "" : projectId.trim();
        if (cleanProjectId.isEmpty()) {
            throw new IllegalArgumentException("Ghostex project id is required.");
        }
        String cleanDirection = direction.trim();
        if (!"up".equals(cleanDirection) && !"down".equals(cleanDirection)) {
            throw new IllegalArgumentException("Unsupported Ghostex project move direction: " + direction);
        }
        return "ghostex move-project --json --project-id " + shellQuote(cleanProjectId) +
            " --direction " + shellQuote(cleanDirection);
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
            case "acknowledge-session-attention":
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

}
