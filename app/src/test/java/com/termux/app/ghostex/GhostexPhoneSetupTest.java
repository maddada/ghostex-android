package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexPhoneSetupTest {

    /*
    CDXC:AndroidOnboarding 2026-05-17-10:51:
    The one-tap phone setup path is part of the release onboarding contract.
    Keep the install command explicit and limited to SSH tooling so it does not
    become a hidden general-purpose package mutation surface.

    CDXC:AndroidConnectionRecovery 2026-05-17-13:03:
    Known-host repair should target only the selected machine's current SSH
    host/port and must quote the host key target before running ssh-keygen.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:20:
    IPv6 default-port repair should remove both raw and bracketed known_hosts
    target shapes because OpenSSH tooling can address the same endpoint through
    either representation.

    CDXC:AndroidSideBySideInstall 2026-05-17-23:58:
    Install SSH should repair executable bits on the patched side-by-side
    bootstrap package manager before invoking `pkg`, so early bad installs can
    recover without clearing app data.

    CDXC:AndroidSideBySideInstall 2026-05-18-00:02:
    Permission repair should use the absolute Ghostex prefix, because the setup
    terminal can fail before `$PREFIX` is exported.

    CDXC:AndroidSideBySideInstall 2026-05-18-00:14:
    Setup terminal failures should print enough device-side diagnostics to show
    whether `pkg` failed because chmod failed, the prefix path is wrong, or the
    executable bit is still missing.

    CDXC:AndroidOnboarding 2026-05-18-00:36:
    Install SSH should preselect one package mirror and use apt-get directly so
    setup output stays short instead of printing Termux's full mirror scan.

    CDXC:AndroidSideBySideInstall 2026-05-18-00:50:
    Side-by-side setup must force apt's cache root to Ghostex Android's package
    data so OpenSSH install does not fail on a missing com.termux archive path.

    CDXC:AndroidSideBySideInstall 2026-05-18-01:01:
    SSH tool setup should repack downloaded Termux deb archives for Ghostex's
    app id before invoking dpkg, because official packages unpack to
    `/data/data/com.termux` by default.

    CDXC:AndroidOnboarding 2026-05-18-01:11:
    The visible setup terminal should stay open after Install SSH finishes, so
    failures remain copyable from the phone instead of disappearing behind
    Android's completed-process screen.

    CDXC:AndroidSideBySideInstall 2026-05-18-01:18:
    Package repack errors must be setup failures, maintainer scripts need valid
    executable permissions, and success requires actual ssh and sshpass binaries.

    CDXC:AndroidOnboarding 2026-05-18-01:19:
    Use non-login bash to keep logs open without triggering Termux bootstrap
    second-stage fallback output after setup.
    */

    @Test
    public void installCommandInstallsOnlySshToolsNeededByGhostexAndroid() {
        String command = GhostexPhoneSetup.INSTALL_SSH_TOOLS_COMMAND;

        Assert.assertTrue(command.startsWith("echo 'Ghostex Android phone setup diagnostics'"));
        Assert.assertTrue(command.contains("/system/bin/ls -ld \"/data/data/com.ghostx\""));
        Assert.assertTrue(command.contains("/system/bin/chmod 700 \"/data/data/com.ghostx/files/usr/bin/pkg\""));
        Assert.assertTrue(command.contains("chmod exit=$chmod_status"));
        Assert.assertTrue(command.contains("Ghostex setup cannot execute pkg after chmod repair."));
        Assert.assertTrue(command.contains("https://packages-cf.termux.dev/apt/termux-main"));
        Assert.assertTrue(command.contains("chosen_mirrors/termux-main"));
        Assert.assertTrue(command.contains("\"/data/data/com.ghostx/files/usr/bin/apt-get\" update -y"));
        Assert.assertTrue(command.contains("\"/data/data/com.ghostx/files/usr/bin/apt-get\" install -y"));
        Assert.assertTrue(command.contains("--download-only"));
        Assert.assertTrue(command.contains("Dir::Cache=\"/data/data/com.ghostx/cache/apt\""));
        Assert.assertTrue(command.contains("Dir::Cache::archives=\"/data/data/com.ghostx/cache/apt/archives\""));
        Assert.assertTrue(command.contains("Patching downloaded packages for Ghostex Android..."));
        Assert.assertTrue(command.contains("\"/data/data/com.ghostx/files/usr/bin/dpkg-deb\" -R \"$deb\" \"$work_dir\" || exit 100"));
        Assert.assertTrue(command.contains("find \"$work_dir/DEBIAN\" -type f -exec /system/bin/chmod 755 {} +"));
        Assert.assertTrue(command.contains("s#/data/data/com.termux#/data/data/com.ghostx#g"));
        Assert.assertTrue(command.contains("\"/data/data/com.ghostx/files/usr/bin/dpkg-deb\" -b \"$work_dir\" \"$patched_deb\" || exit 100"));
        Assert.assertTrue(command.contains("\"/data/data/com.ghostx/files/usr/bin/dpkg\" -i \"/data/data/com.ghostx/files/usr/tmp/ghostex-patched-debs\"/*.deb"));
        Assert.assertTrue(command.contains("[ -x \"/data/data/com.ghostx/files/usr/bin/ssh\" ]"));
        Assert.assertTrue(command.contains("[ -x \"/data/data/com.ghostx/files/usr/bin/sshpass\" ]"));
        Assert.assertTrue(command.contains("openssh sshpass"));
        Assert.assertFalse(command.contains("pkg\" update"));
        Assert.assertFalse(command.contains("pkg\" install"));
        Assert.assertFalse(command.contains("&& rm"));
        Assert.assertFalse(command.contains(";"));
    }

    @Test
    public void interactiveInstallCommandKeepsSetupTerminalOpenForLogs() {
        String command = GhostexPhoneSetup.INSTALL_SSH_TOOLS_INTERACTIVE_COMMAND;

        Assert.assertTrue(command.startsWith("ghostex_install_ssh_tools()"));
        Assert.assertTrue(command.contains("ghostex_install_ssh_tools\nsetup_status=$?"));
        Assert.assertTrue(command.contains("Ghostex setup finished with exit code $setup_status"));
        Assert.assertTrue(command.contains("Review or copy this log, then type exit to close."));
        Assert.assertTrue(command.contains("exec \"/data/data/com.ghostx/files/usr/bin/bash\""));
        Assert.assertFalse(command.contains("bash\" -l"));
        Assert.assertTrue(command.contains("return 126"));
        Assert.assertTrue(command.contains("return 100"));
        Assert.assertFalse(command.contains("exit 126"));
        Assert.assertFalse(command.contains("exit 100"));
    }

    @Test
    public void resetKnownHostCommandTargetsDefaultSshHost() {
        GhostexMachine machine = new GhostexMachine("machine-1", "Mac", "mac.tailnet.ts.net",
            "madda", 22, false, 0L);

        String command = GhostexPhoneSetup.buildResetKnownHostCommand(machine);

        Assert.assertEquals("mkdir -p ~/.ssh && touch ~/.ssh/known_hosts && ssh-keygen -R 'mac.tailnet.ts.net' -f ~/.ssh/known_hosts", command);
    }

    @Test
    public void resetKnownHostCommandTargetsCustomPort() {
        GhostexMachine machine = new GhostexMachine("machine-1", "Mac", "mac.tailnet.ts.net",
            "madda", 2222, false, 0L);

        String command = GhostexPhoneSetup.buildResetKnownHostCommand(machine);

        Assert.assertEquals("mkdir -p ~/.ssh && touch ~/.ssh/known_hosts && ssh-keygen -R '[mac.tailnet.ts.net]:2222' -f ~/.ssh/known_hosts", command);
    }

    @Test
    public void resetKnownHostCommandTargetsIpv6DefaultPortForms() {
        GhostexMachine machine = new GhostexMachine("machine-1", "IPv6 Mac", "fd7a:115c:a1e0::42",
            "madda", 22, false, 0L);

        String command = GhostexPhoneSetup.buildResetKnownHostCommand(machine);

        Assert.assertEquals("mkdir -p ~/.ssh && touch ~/.ssh/known_hosts && ssh-keygen -R 'fd7a:115c:a1e0::42' -f ~/.ssh/known_hosts && ssh-keygen -R '[fd7a:115c:a1e0::42]:22' -f ~/.ssh/known_hosts", command);
    }

    @Test
    public void resetKnownHostCommandTargetsIpv6CustomPortOnly() {
        GhostexMachine machine = new GhostexMachine("machine-1", "IPv6 Mac", "fd7a:115c:a1e0::42",
            "madda", 2222, false, 0L);

        String command = GhostexPhoneSetup.buildResetKnownHostCommand(machine);

        Assert.assertEquals("mkdir -p ~/.ssh && touch ~/.ssh/known_hosts && ssh-keygen -R '[fd7a:115c:a1e0::42]:2222' -f ~/.ssh/known_hosts", command);
    }

}
