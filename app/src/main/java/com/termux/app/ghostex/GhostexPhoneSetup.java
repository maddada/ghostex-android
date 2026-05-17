package com.termux.app.ghostex;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class GhostexPhoneSetup {

    private static final String TERMUX_PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    private static final String GHOSTEX_APP_DATA_ROOT = TermuxConstants.TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH;
    private static final String DEFAULT_MAIN_MIRROR = "https://packages-cf.termux.dev/apt/termux-main";
    private static final String APT_CACHE_OPTIONS =
        "-o Dir::Cache=\"" + GHOSTEX_APP_DATA_ROOT + "/cache/apt\" " +
            "-o Dir::Cache::archives=\"" + GHOSTEX_APP_DATA_ROOT + "/cache/apt/archives\"";
    private static final String APT_ARCHIVES_DIR = GHOSTEX_APP_DATA_ROOT + "/cache/apt/archives";
    private static final String PATCHED_DEBS_DIR = TERMUX_PREFIX + "/tmp/ghostex-patched-debs";
    private static final String DEB_WORK_DIR = TERMUX_PREFIX + "/tmp/ghostex-deb-work";

    public static final String REPAIR_BOOTSTRAP_EXECUTABLES_COMMAND =
        "echo 'Ghostex Android phone setup diagnostics'\n" +
            "echo \"uid=$(id)\"\n" +
            "echo \"PATH=$PATH\"\n" +
            "/system/bin/ls -ld \"/data/data/com.ghostx\" \"/data/data/com.ghostx/files\" \"" + TERMUX_PREFIX + "\" \"" + TERMUX_PREFIX + "/bin\" 2>&1 || true\n" +
            "/system/bin/ls -l \"" + TERMUX_PREFIX + "/bin/pkg\" \"" + TERMUX_PREFIX + "/bin/bash\" \"" + TERMUX_PREFIX + "/bin/apt\" \"" + TERMUX_PREFIX + "/bin/apt-get\" 2>&1 || true\n" +
            "echo 'Running chmod repair for Ghostex package tools...'\n" +
            "/system/bin/chmod 700 \"" + TERMUX_PREFIX + "/bin/pkg\" \"" + TERMUX_PREFIX + "/bin/bash\" \"" + TERMUX_PREFIX + "/bin/apt\" \"" + TERMUX_PREFIX + "/bin/apt-get\" 2>&1\n" +
            "chmod_status=$?\n" +
            "echo \"chmod exit=$chmod_status\"\n" +
            "/system/bin/ls -l \"" + TERMUX_PREFIX + "/bin/pkg\" \"" + TERMUX_PREFIX + "/bin/bash\" \"" + TERMUX_PREFIX + "/bin/apt\" \"" + TERMUX_PREFIX + "/bin/apt-get\" 2>&1 || true\n" +
            "echo 'Repairing Ghostex package cache paths...'\n" +
            "/system/bin/mkdir -p \"" + GHOSTEX_APP_DATA_ROOT + "/cache/apt/archives/partial\"\n" +
            "\"" + TERMUX_PREFIX + "/bin/sed\" -i 's#/data/data/com.termux#/data/data/com.ghostx#g' \"" + TERMUX_PREFIX + "/bin/pkg\" \"" + TERMUX_PREFIX + "/etc/apt/sources.list\" \"" + TERMUX_PREFIX + "/etc/apt/sources.list.d/\"*.list 2>/dev/null || true\n" +
            "/system/bin/ls -ld \"" + GHOSTEX_APP_DATA_ROOT + "/cache/apt\" \"" + GHOSTEX_APP_DATA_ROOT + "/cache/apt/archives\" \"" + GHOSTEX_APP_DATA_ROOT + "/cache/apt/archives/partial\" 2>&1 || true\n" +
            "echo 'Selecting Ghostex package mirror...'\n" +
            "/system/bin/mkdir -p \"" + TERMUX_PREFIX + "/etc/termux/chosen_mirrors\"\n" +
            "echo \"" + DEFAULT_MAIN_MIRROR + "\" > \"" + TERMUX_PREFIX + "/etc/termux/chosen_mirrors/termux-main\"\n" +
            "echo 'deb " + DEFAULT_MAIN_MIRROR + " stable main' > \"" + TERMUX_PREFIX + "/etc/apt/sources.list\"\n" +
            "if [ ! -x \"" + TERMUX_PREFIX + "/bin/pkg\" ]\n" +
            "then\n" +
            "echo 'Ghostex setup cannot execute pkg after chmod repair.'\n" +
            "exit 126\n" +
            "fi\n" +
            "if [ ! -x \"" + TERMUX_PREFIX + "/bin/bash\" ]\n" +
            "then\n" +
            "echo 'Ghostex setup cannot execute bash after chmod repair.'\n" +
            "exit 126\n" +
            "fi";
    private static final String PATCH_TERMUX_PACKAGE_ARCHIVES_COMMAND =
        "echo 'Patching downloaded packages for Ghostex Android...'\n" +
            "/system/bin/rm -rf \"" + PATCHED_DEBS_DIR + "\" \"" + DEB_WORK_DIR + "\"\n" +
            "/system/bin/mkdir -p \"" + PATCHED_DEBS_DIR + "\" \"" + DEB_WORK_DIR + "\"\n" +
            "for deb in \"" + APT_ARCHIVES_DIR + "\"/*.deb\n" +
            "do\n" +
            "if [ ! -e \"$deb\" ]\n" +
            "then\n" +
            "echo 'No downloaded packages were found to patch.'\n" +
            "exit 100\n" +
            "fi\n" +
            "deb_name=${deb##*/}\n" +
            "work_dir=\"" + DEB_WORK_DIR + "/${deb_name%.deb}\"\n" +
            "patched_deb=\"" + PATCHED_DEBS_DIR + "/$deb_name\"\n" +
            "/system/bin/rm -rf \"$work_dir\"\n" +
            "/system/bin/mkdir -p \"$work_dir\"\n" +
            "\"" + TERMUX_PREFIX + "/bin/dpkg-deb\" -R \"$deb\" \"$work_dir\" || exit 100\n" +
            "if [ -d \"$work_dir/data/data/com.termux\" ]\n" +
            "then\n" +
            "/system/bin/mkdir -p \"$work_dir/data/data/com.ghostx\"\n" +
            "for package_path in \"$work_dir/data/data/com.termux/\"*\n" +
            "do\n" +
            "if [ -e \"$package_path\" ]\n" +
            "then\n" +
            "/system/bin/mv \"$package_path\" \"$work_dir/data/data/com.ghostx/\"\n" +
            "fi\n" +
            "done\n" +
            "/system/bin/rmdir \"$work_dir/data/data/com.termux\" 2>/dev/null || true\n" +
            "fi\n" +
            "/system/bin/chmod 700 \"$work_dir/data/data/com.ghostx\" 2>/dev/null || true\n" +
            "/system/bin/chmod 771 \"$work_dir/data/data/com.ghostx/files\" 2>/dev/null || true\n" +
            "/system/bin/chmod 700 \"$work_dir/data/data/com.ghostx/files/usr\" 2>/dev/null || true\n" +
            "/system/bin/find \"$work_dir/DEBIAN\" -type f -exec /system/bin/chmod 755 {} + 2>/dev/null || true\n" +
            "\"" + TERMUX_PREFIX + "/bin/find\" \"$work_dir\" -type f -exec \"" + TERMUX_PREFIX + "/bin/sed\" -i 's#/data/data/com.termux#/data/data/com.ghostx#g' {} +\n" +
            "\"" + TERMUX_PREFIX + "/bin/dpkg-deb\" -b \"$work_dir\" \"$patched_deb\" || exit 100\n" +
            "done";
    public static final String INSTALL_SSH_TOOLS_COMMAND =
        REPAIR_BOOTSTRAP_EXECUTABLES_COMMAND + "\n" +
            "echo 'Updating package metadata...'\n" +
            "\"" + TERMUX_PREFIX + "/bin/apt-get\" update -y -o Dpkg::Use-Pty=0 " + APT_CACHE_OPTIONS + " && " +
            "echo 'Downloading OpenSSH and sshpass...'\n" +
            "\"" + TERMUX_PREFIX + "/bin/apt-get\" install -y --download-only -o Dpkg::Use-Pty=0 " + APT_CACHE_OPTIONS + " openssh sshpass && " +
            PATCH_TERMUX_PACKAGE_ARCHIVES_COMMAND + "\n" +
            "echo 'Installing patched OpenSSH and sshpass packages...'\n" +
            "\"" + TERMUX_PREFIX + "/bin/dpkg\" -i \"" + PATCHED_DEBS_DIR + "\"/*.deb && " +
            "[ -x \"" + TERMUX_PREFIX + "/bin/ssh\" ] && [ -x \"" + TERMUX_PREFIX + "/bin/sshpass\" ] && " +
            "echo && echo 'Ghostex Android phone setup complete.'";
    public static final String INSTALL_SSH_TOOLS_INTERACTIVE_COMMAND =
        "ghostex_install_ssh_tools() {\n" +
            INSTALL_SSH_TOOLS_COMMAND
                .replace("exit 126", "return 126")
                .replace("exit 100", "return 100") + "\n" +
            "}\n" +
            "ghostex_install_ssh_tools\n" +
            "setup_status=$?\n" +
            "echo\n" +
            "echo \"Ghostex setup finished with exit code $setup_status. Review or copy this log, then type exit to close.\"\n" +
            "exec \"" + TERMUX_PREFIX + "/bin/bash\"";

    private final GhostexTermuxShell termuxShell;

    /*
    CDXC:AndroidOnboarding 2026-05-17-10:51:
    Release UX should not make users infer missing phone-side SSH tools from a
    failed remote reconnect. Check local `ssh` and `sshpass` before network
    inventory refreshes, and provide one repair command that runs in the
    existing Termux terminal surface when setup is incomplete.

    CDXC:AndroidOnboarding 2026-05-17-18:24:
    Setup checks should use the full Termux shell environment, not a partial
    Android process environment, so the preflight reflects the same runtime
    used by reconnect and visible setup terminals.

    CDXC:AndroidConnectionRecovery 2026-05-17-13:03:
    Host-key mismatch is a common SSH recovery case after a Mac is reinstalled
    or Tailscale host identity changes. Provide a targeted known_hosts repair
    command for the selected saved machine instead of asking users to edit
    Termux files manually.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:20:
    IPv6 default-port SSH can appear in known_hosts as either the raw literal or
    the bracketed `[host]:22` form. Remove both current-target shapes so the
    confirmed repair action actually clears the key used by the next reconnect.

    CDXC:AndroidSideBySideInstall 2026-05-17-23:58:
    Early side-by-side installs can leave patched bootstrap commands without
    executable bits in the existing app-private prefix. Repair the core package
    manager executables immediately before Install SSH runs so users do not
    need to clear app data just to recover from `pkg: Permission denied`.

    CDXC:AndroidSideBySideInstall 2026-05-18-00:02:
    The visible setup terminal can hit the package manager before `$PREFIX` is
    exported, so the permission repair uses the absolute Ghostex private prefix
    instead of relying on shell environment setup.

    CDXC:AndroidSideBySideInstall 2026-05-18-00:14:
    Phone setup failures must explain why `pkg` cannot execute on the device.
    Print uid, PATH, prefix directory modes, tool modes, chmod output, and
    explicit executable checks in the setup terminal before running `pkg`.

    CDXC:AndroidSideBySideInstall 2026-05-18-00:32:
    Early side-by-side APKs left `pkg` pointing at `/data/data/com.termux/cache`.
    Repair that old app-data root inside package-manager text files and create
    Ghostex's own apt cache directory before the install command runs.

    CDXC:AndroidOnboarding 2026-05-18-00:36:
    Install SSH should not dump Termux's full mirror auto-selection list into
    the setup terminal. Preselect a stable default mirror and call `apt-get`
    directly so users see a short Ghostex setup log instead of hundreds of
    mirror status lines.

    CDXC:AndroidSideBySideInstall 2026-05-18-00:50:
    The patched apt-get binary can still default archive downloads to
    `/data/data/com.termux/cache`. Pass explicit Dir::Cache options on update
    and install so side-by-side setup writes only into Ghostex Android's cache.

    CDXC:AndroidSideBySideInstall 2026-05-18-01:01:
    Official Termux repository packages contain `/data/data/com.termux` payload
    paths. Download the required SSH packages, rewrite their same-length app
    data root to `com.ghostx`, rebuild the local debs, and install those patched
    archives so side-by-side Ghostex Android can bootstrap SSH without a custom
    package repository yet.

    CDXC:AndroidSideBySideInstall 2026-05-18-01:05:
    Repacked debs must preserve Android app-private directory modes for
    `/data/data/com.ghostx`, `/files`, and `/files/usr`; do not let package
    directory entries weaken the sandbox while fixing the package id path.

    CDXC:AndroidOnboarding 2026-05-18-01:11:
    The visible Install SSH terminal must remain open after success or failure
    so phone-side package logs can be reviewed and copied. Run setup inside a
    function, capture its status, then enter an interactive bash shell instead
    of letting Android close the one-shot command session.

    CDXC:AndroidSideBySideInstall 2026-05-18-01:18:
    Repacking must fail the setup when any deb fails to rebuild, normalize
    DEBIAN maintainer-script modes to dpkg-deb's accepted executable range, and
    verify both ssh and sshpass exist before reporting success.

    CDXC:AndroidOnboarding 2026-05-18-01:19:
    Keep the setup log open with non-login bash. Login bash can trigger Termux's
    bootstrap second-stage fallback, which still contains upstream com.termux
    paths and adds confusing post-setup noise.
    */
    public GhostexPhoneSetup(@NonNull Context context) {
        termuxShell = new GhostexTermuxShell(context);
    }

    public Status check(boolean requiresSavedPasswordAutomation) {
        boolean hasSsh = commandExists("ssh");
        boolean hasSshpass = commandExists("sshpass");
        if (!hasSsh) {
            return Status.missing("Install phone SSH tools before connecting. Tap Setup, then Install SSH tools.");
        }
        if (requiresSavedPasswordAutomation && !hasSshpass) {
            return Status.missing("Saved-password reconnects need sshpass on this phone. Tap Setup, then Install SSH tools.");
        }
        return Status.ready(hasSshpass);
    }

    public Status resetKnownHost(@NonNull GhostexMachine machine) {
        if (!commandExists("ssh-keygen")) {
            return Status.missing("Install OpenSSH before resetting saved SSH host keys.");
        }
        CommandResult result = runCommand(buildResetKnownHostCommand(machine));
        if (result.exitCode != 0) {
            return Status.missing(result.output.isEmpty()
                ? "Could not reset the saved SSH host key for " + machine.connectionTarget() + "."
                : result.output);
        }
        return Status.success("Saved SSH host key reset for " + machine.connectionTarget() + ". Retrying connection...");
    }

    static String buildResetKnownHostCommand(@NonNull GhostexMachine machine) {
        StringBuilder command = new StringBuilder("mkdir -p ~/.ssh && touch ~/.ssh/known_hosts");
        for (String target : knownHostRemovalTargets(machine)) {
            command.append(" && ssh-keygen -R ")
                .append(GhostexSshCommandBuilder.shellQuote(target))
                .append(" -f ~/.ssh/known_hosts");
        }
        return command.toString();
    }

    static List<String> knownHostRemovalTargets(@NonNull GhostexMachine machine) {
        ArrayList<String> targets = new ArrayList<>();
        if (machine.port != 22) {
            targets.add("[" + machine.host + "]:" + machine.port);
            return targets;
        }
        targets.add(machine.host);
        if (machine.host.contains(":")) targets.add("[" + machine.host + "]:22");
        return targets;
    }

    private boolean commandExists(@NonNull String commandName) {
        return runCommand("command -v " + commandName).exitCode == 0;
    }

    private CommandResult runCommand(@NonNull String command) {
        try {
            Process process = termuxShell.newShellProcess(command).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) output.append('\n');
                    output.append(line);
                }
            }
            return new CommandResult(process.waitFor(), output.toString().trim());
        } catch (Exception ignored) {
            return new CommandResult(-1, "");
        }
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;

        CommandResult(int exitCode, @NonNull String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public static final class Status {
        public final boolean ready;
        public final boolean sshpassInstalled;
        public final String message;

        private Status(boolean ready, boolean sshpassInstalled, @NonNull String message) {
            this.ready = ready;
            this.sshpassInstalled = sshpassInstalled;
            this.message = message;
        }

        public static Status ready(boolean sshpassInstalled) {
            return new Status(true, sshpassInstalled, sshpassInstalled
                ? "Phone SSH tools are installed."
                : "OpenSSH is installed. Save-password automation needs sshpass.");
        }

        public static Status success(@NonNull String message) {
            return new Status(true, false, message);
        }

        public static Status missing(@NonNull String message) {
            return new Status(false, false, message);
        }
    }

}
