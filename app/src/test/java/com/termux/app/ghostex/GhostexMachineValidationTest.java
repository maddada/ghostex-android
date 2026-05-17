package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexMachineValidationTest {

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-18:46:
    Saved-machine validation protects release UX by catching invalid SSH target
    fields in the editor instead of letting reconnect fail with raw SSH errors.

    CDXC:AndroidConnectionSecurity 2026-05-17-13:06:
    Machine validation is a security boundary for saved reconnect targets. It
    should accept normal Tailscale/MagicDNS/IP inputs while rejecting shell
    metacharacters and malformed SSH host/account tokens before persistence.

    CDXC:AndroidConnectionSecurity 2026-05-17-14:57:
    Machine ids are used as secondary storage keys and should stay simple even
    when old settings JSON is loaded from disk.
    */
    @Test
    public void acceptsTailscaleHostnamesIpsAndNormalUsers() {
        Assert.assertNull(GhostexMachineValidation.hostError("macbook.tailnet.ts.net"));
        Assert.assertNull(GhostexMachineValidation.hostError("100.64.0.42"));
        Assert.assertNull(GhostexMachineValidation.hostError("fd7a:115c:a1e0::42"));
        Assert.assertNull(GhostexMachineValidation.usernameError("madda"));
        Assert.assertNull(GhostexMachineValidation.usernameError("build.user_1"));
        Assert.assertNull(GhostexMachineValidation.machineIdError("machine-1_2.3"));
    }

    @Test
    public void rejectsAmbiguousOrOptionLikeSshTargets() {
        Assert.assertNotNull(GhostexMachineValidation.hostError("madda@macbook.tailnet.ts.net"));
        Assert.assertNotNull(GhostexMachineValidation.hostError("mac book"));
        Assert.assertNotNull(GhostexMachineValidation.hostError("-oProxyCommand=bad"));
        Assert.assertNotNull(GhostexMachineValidation.usernameError("ma dda"));
        Assert.assertNotNull(GhostexMachineValidation.usernameError("madda@mac"));
        Assert.assertNotNull(GhostexMachineValidation.usernameError("-lroot"));
    }

    @Test
    public void rejectsHostAndUsernameShellMetacharacters() {
        Assert.assertNotNull(GhostexMachineValidation.hostError("macbook;shutdown"));
        Assert.assertNotNull(GhostexMachineValidation.hostError("macbook$(whoami)"));
        Assert.assertNotNull(GhostexMachineValidation.hostError("macbook'test"));
        Assert.assertNotNull(GhostexMachineValidation.hostError("[fd7a:115c:a1e0::42]"));
        Assert.assertNotNull(GhostexMachineValidation.hostError("fd7a:zzzz::42"));
        Assert.assertNotNull(GhostexMachineValidation.usernameError("madda;root"));
        Assert.assertNotNull(GhostexMachineValidation.usernameError("madda/root"));
        Assert.assertNotNull(GhostexMachineValidation.usernameError("madda$USER"));
        Assert.assertNotNull(GhostexMachineValidation.machineIdError("machine/1"));
        Assert.assertNotNull(GhostexMachineValidation.machineIdError("machine 1"));
    }

    @Test
    public void parsesOnlyValidTcpPorts() {
        Assert.assertEquals(22, GhostexMachineValidation.parsePort("22"));
        Assert.assertEquals(65535, GhostexMachineValidation.parsePort("65535"));
        Assert.assertEquals(-1, GhostexMachineValidation.parsePort("0"));
        Assert.assertEquals(-1, GhostexMachineValidation.parsePort("65536"));
        Assert.assertEquals(-1, GhostexMachineValidation.parsePort("ssh"));
    }

}
