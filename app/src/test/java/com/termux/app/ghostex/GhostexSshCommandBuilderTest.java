package com.termux.app.ghostex;

import androidx.annotation.NonNull;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexSshCommandBuilderTest {

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-10:37:
    SSH command construction is the app's bridge to the Mac-hosted Ghostex CLI.
    Keep focused tests around quoting and remote Ghostex command construction
    so Android does not regress into unsafe command strings.

    CDXC:AndroidRemoteSessions 2026-05-17-14:09:
    Android attach commands should send stable session ids to the Ghostex CLI
    rather than mutable sidebar aliases.

    CDXC:AndroidSidebar 2026-05-17-14:34:
    Rename should be a tested command-builder path because it accepts user text
    and runs remotely through SSH.

    CDXC:AndroidRemoteSessions 2026-05-17-12:41:
    The Mac-side command should run under `/bin/zsh -lc` so SSH sees the same
    login-shell PATH where Homebrew-installed `ghostex` is normally available.

    CDXC:AndroidConnectionManagement 2026-05-17-14:03:
    Saved-machine Check connection uses a remote Ghostex CLI readiness command,
    so keep the shell shape covered beside list, attach, and context actions.

    CDXC:AndroidRemoteSessions 2026-05-17-13:57:
    Android context-menu lifecycle and focus commands use `--session-id` flag
    forms so every remote action has the same stable selector shape as rename.

    CDXC:AndroidRemoteSessions 2026-05-17-13:59:
    Attach uses the same `--session-id` flag form so the primary terminal-open
    path is documented and stable-id based too.

    CDXC:AndroidConnectionManagement 2026-05-17-14:07:
    Check connection should explicitly verify both `ghostex` and `zmx` on the
    Mac so readiness checks catch the first-release ZMX dependency before users
    try to attach.

    CDXC:AndroidConnectionManagement 2026-05-17-18:20:
    Check connection should use the Mac-side `ghostex android-check --json`
    contract so Android validates zmx settings and bridge inventory through one
    release-supported CLI command.

    CDXC:AndroidConnectionManagement 2026-05-17-14:16:
    IPv6 SSH destinations should use the same bracketed host form as Settings
    and clipboard copy so command execution stays aligned with visible machine
    targets.

    CDXC:AndroidRemoteSessions 2026-05-17-14:29:
    Cold session attach should use a short SSH connection timeout like
    inventory and remote actions, keeping offline Tailscale hosts from hanging
    the foreground terminal for the SSH default timeout.

    CDXC:AndroidRemoteSessions 2026-05-17-14:55:
    Session action names are command tokens. The builder must reject unsupported
    actions so future UI code cannot smuggle arbitrary shell text into the
    Mac-side Ghostex command.

    CDXC:AndroidRemoteSessions 2026-05-17-17:12:
    The builder should also reject missing stable session ids. A blank
    `--session-id` selector is never a valid Android remote action target.

    CDXC:AndroidRemoteSessions 2026-05-18-02:31:
    Android project-header creation should call `ghostex create-session` with
    project/group context so the Mac app creates a zmx-backed session when zmx
    persistence is enabled in Settings.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Android project reordering uses `ghostex move-project` so desktop sidebar
    persistence owns project placement and later session inventory returns the
    same order to mobile.
    */

    @Test
    public void shellQuoteEscapesSingleQuotes() {
        Assert.assertEquals("'it'\"'\"'s fine'", GhostexSshCommandBuilder.shellQuote("it's fine"));
    }

    @Test
    public void copyableAttachCommandUsesInteractiveSshWithoutSshpass() {
        GhostexRemoteSession session = session();

        String command = GhostexSshCommandBuilder.buildCopyableAttachCommand(machine(), session);

        Assert.assertTrue(command.startsWith("ssh -tt"));
        Assert.assertFalse(command.contains("sshpass"));
        Assert.assertTrue(command.contains("'madda@mac.tailnet.ts.net'"));
        String quotedRemoteCommand = GhostexSshCommandBuilder.shellQuote(
            GhostexSshCommandBuilder.loginShellCommand("ghostex attach --session-id " + GhostexSshCommandBuilder.shellQuote(session.sessionId)));
        Assert.assertTrue(command.contains(quotedRemoteCommand));
        Assert.assertFalse(command.contains("ghostex attach " + GhostexSshCommandBuilder.shellQuote(session.alias)));
    }

    @Test
    public void sessionActionsUseStableSessionId() {
        GhostexRemoteSession session = session();

        String command = GhostexSshCommandBuilder.buildSessionActionCommand(machine(), session, "sleep", false);

        Assert.assertEquals("ghostex sleep --session-id " + GhostexSshCommandBuilder.shellQuote(session.sessionId) + " --json",
            command);
        Assert.assertFalse(command.contains("ghostex sleep " + GhostexSshCommandBuilder.shellQuote(session.alias)));
    }

    @Test
    public void sessionActionsRejectUnsupportedShellTokens() {
        try {
            GhostexSshCommandBuilder.buildSessionActionCommand(machine(), session(), "sleep; rm -rf ~", false);
            Assert.fail("Expected unsupported action to fail.");
        } catch (IllegalArgumentException error) {
            Assert.assertEquals("Unsupported Ghostex session action: sleep; rm -rf ~", error.getMessage());
        }
    }

    @Test
    public void remoteSessionCommandsRejectMissingSessionId() {
        GhostexRemoteSession session = new GhostexRemoteSession(
            "1",
            "",
            "project-1",
            "Work",
            "Ghostex",
            "/Users/madda/dev/ghostex",
            "idle",
            "running",
            "zmx",
            "zmx-work",
            "codex",
            "2026-05-17T10:00:00Z",
            false,
            false
        );

        assertMissingSessionIdFails(() -> GhostexSshCommandBuilder.attachRemoteCommand(session));
        assertMissingSessionIdFails(() -> GhostexSshCommandBuilder.buildSessionActionCommand(machine(), session, "sleep", false));
        assertMissingSessionIdFails(() -> GhostexSshCommandBuilder.buildRenameSessionCommand(machine(), session, "Title", false));
    }

    @Test
    public void renameSessionUsesSessionIdAndTitleFlag() {
        GhostexRemoteSession session = session();

        String command = GhostexSshCommandBuilder.buildRenameSessionCommand(machine(), session, "Ship Android's polish", true);

        Assert.assertEquals("ghostex rename-session --session-id " +
            GhostexSshCommandBuilder.shellQuote(session.sessionId) + " --title=" +
            GhostexSshCommandBuilder.shellQuote("Ship Android's polish") + " --json", command);
        Assert.assertFalse(command.contains("rename-session " + GhostexSshCommandBuilder.shellQuote(session.alias)));
    }

    @Test
    public void createSessionUsesProjectAndGroupFlags() {
        String command = GhostexSshCommandBuilder.buildCreateSessionCommand(machine(),
            "project-1", "group-main", false);

        Assert.assertEquals("ghostex create-session --json --project-id " +
            GhostexSshCommandBuilder.shellQuote("project-1") + " --group-id " +
            GhostexSshCommandBuilder.shellQuote("group-main"), command);
    }

    @Test
    public void moveProjectUsesProjectIdAndDirectionFlags() {
        String command = GhostexSshCommandBuilder.moveProjectRemoteCommand("project-1", "up");

        Assert.assertEquals("ghostex move-project --json --project-id " +
            GhostexSshCommandBuilder.shellQuote("project-1") + " --direction " +
            GhostexSshCommandBuilder.shellQuote("up"), command);
    }

    @Test
    public void moveProjectRejectsUnsupportedDirection() {
        try {
            GhostexSshCommandBuilder.moveProjectRemoteCommand("project-1", "up; rm -rf ~");
            Assert.fail("Expected unsupported direction to fail.");
        } catch (IllegalArgumentException error) {
            Assert.assertEquals("Unsupported Ghostex project move direction: up; rm -rf ~", error.getMessage());
        }
    }

    @Test
    public void loginShellCommandQuotesNestedRemoteCommand() {
        Assert.assertEquals("/bin/zsh -lc 'ghostex attach --session-id '\"'\"'session-1'\"'\"''",
            GhostexSshCommandBuilder.loginShellCommand("ghostex attach --session-id " + GhostexSshCommandBuilder.shellQuote("session-1")));
    }

    private GhostexMachine machine() {
        return new GhostexMachine("machine-1", "Mac", "mac.tailnet.ts.net", "madda", 2222, true, 0L);
    }

    private GhostexRemoteSession session() {
        return new GhostexRemoteSession(
            "work's-main",
            "session-1",
            "project-1",
            "Work",
            "Ghostex",
            "/Users/madda/dev/ghostex",
            "idle",
            "running",
            "zmx",
            "zmx-work",
            "codex",
            "2026-05-17T10:00:00Z",
            false,
            false
        );
    }

    private void assertMissingSessionIdFails(@NonNull Runnable commandBuilder) {
        try {
            commandBuilder.run();
            Assert.fail("Expected missing session id to fail.");
        } catch (IllegalArgumentException error) {
            Assert.assertEquals("Ghostex remote session id is required.", error.getMessage());
        }
    }

}
