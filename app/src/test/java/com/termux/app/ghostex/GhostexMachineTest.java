package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class GhostexMachineTest {

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-12:33:
    Password recovery can convert a saved-password machine to session-only use
    when the user unchecks Save password. The machine update must preserve the
    SSH identity while disabling automatic saved-password reconnect.

    CDXC:AndroidConnectionManagement 2026-05-17-12:45:
    Saved-machine labels should include the SSH target where users switch
    accounts, so multiple Macs or usernames do not collapse into ambiguous
    display names.

    CDXC:AndroidConnectionManagement 2026-05-17-14:16:
    IPv6 saved-machine labels must bracket the host before appending custom
    ports so copied support/setup targets remain readable and unambiguous.

    CDXC:AndroidConnectionSecurity 2026-05-17-12:50:
    Saved SSH passwords must stay bound to the host, username, and port they
    were entered for; target edits require a new password before automatic
    saved-password reconnect remains enabled.

    CDXC:AndroidConnectionManagement 2026-05-17-12:54:
    Saved-machine cards should expose selected and Last Connected state so
    multi-machine switching remains obvious from Settings.

    CDXC:AndroidConnectionSecurity 2026-05-17-13:06:
    Persisted machine records participate in startup reconnect, so JSON loading
    should reject records that no longer satisfy the SSH host/account validation
    used by the machine editor.

    CDXC:AndroidConnectionSecurity 2026-05-17-14:15:
    Invalid persisted SSH ports must reject the saved machine instead of being
    coerced to 22, because reconnecting to a different endpoint is less
    predictable than asking the user to re-add the broken account.

    CDXC:AndroidConnectionSecurity 2026-05-17-14:57:
    Persisted machine ids should stay valid storage keys for passwords,
    selected-machine state, and warm-session records.
    */

    @Test
    public void withSavePasswordPreservesMachineIdentityAndConnectionFields() {
        GhostexMachine machine = new GhostexMachine("machine-1", "Mac", "mac.tailnet.ts.net",
            "madda", 2222, true, 1234L);

        GhostexMachine updated = machine.withSavePassword(false);

        Assert.assertEquals(machine.id, updated.id);
        Assert.assertEquals(machine.name, updated.name);
        Assert.assertEquals(machine.host, updated.host);
        Assert.assertEquals(machine.username, updated.username);
        Assert.assertEquals(machine.port, updated.port);
        Assert.assertEquals(machine.lastConnectedAt, updated.lastConnectedAt);
        Assert.assertFalse(updated.savePassword);
    }

    @Test
    public void machineLabelsExposeConnectionTargetForSwitching() {
        GhostexMachine named = new GhostexMachine("machine-1", "Studio Mac", "mac.tailnet.ts.net",
            "madda", 2222, true, 0L);
        GhostexMachine unnamed = new GhostexMachine("machine-2", "", "mini.tailnet.ts.net",
            "shared", 22, false, 0L);

        Assert.assertEquals("madda@mac.tailnet.ts.net:2222", named.connectionTarget());
        Assert.assertEquals("Studio Mac · madda@mac.tailnet.ts.net:2222", named.dropdownLabel());
        Assert.assertEquals("shared@mini.tailnet.ts.net", unnamed.displayLabel());
        Assert.assertEquals("shared@mini.tailnet.ts.net", unnamed.dropdownLabel());
    }

    @Test
    public void machineLabelsBracketIpv6Targets() {
        GhostexMachine defaultPort = new GhostexMachine("machine-1", "", "fd7a:115c:a1e0::42",
            "madda", 22, false, 0L);
        GhostexMachine customPort = new GhostexMachine("machine-2", "IPv6 Mac", "fd7a:115c:a1e0::42",
            "madda", 2222, false, 0L);

        Assert.assertEquals("madda@[fd7a:115c:a1e0::42]", defaultPort.connectionTarget());
        Assert.assertEquals("madda@[fd7a:115c:a1e0::42]:2222", customPort.connectionTarget());
        Assert.assertEquals("IPv6 Mac · madda@[fd7a:115c:a1e0::42]:2222", customPort.dropdownLabel());
    }

    @Test
    public void sameSshTargetRequiresHostUsernameAndPortToMatch() {
        GhostexMachine machine = new GhostexMachine("machine-1", "Studio Mac", "mac.tailnet.ts.net",
            "madda", 2222, true, 0L);

        Assert.assertTrue(machine.hasSameSshTarget("mac.tailnet.ts.net", "madda", 2222));
        Assert.assertFalse(machine.hasSameSshTarget("other.tailnet.ts.net", "madda", 2222));
        Assert.assertFalse(machine.hasSameSshTarget("mac.tailnet.ts.net", "other", 2222));
        Assert.assertFalse(machine.hasSameSshTarget("mac.tailnet.ts.net", "madda", 22));
    }

    @Test
    public void connectionStateLabelShowsSelectedAndLastConnected() {
        long nowMs = 1_779_008_400_000L;
        GhostexMachine connected = new GhostexMachine("machine-1", "Studio Mac", "mac.tailnet.ts.net",
            "madda", 2222, true, nowMs - 120_000L);
        GhostexMachine neverConnected = new GhostexMachine("machine-2", "Mini", "mini.tailnet.ts.net",
            "madda", 22, false, 0L);

        Assert.assertEquals("Selected · Last connected 2m ago", connected.connectionStateLabel(true, nowMs));
        Assert.assertEquals("Last connected 2m ago", connected.connectionStateLabel(false, nowMs));
        Assert.assertEquals("Never connected", neverConnected.connectionStateLabel(false, nowMs));
    }

    @Test
    public void fromJsonRejectsPersistedInvalidSshTargets() throws Exception {
        Assert.assertNotNull(GhostexMachine.fromJson(new org.json.JSONObject()
            .put("id", "machine-1")
            .put("host", "mac.tailnet.ts.net")
            .put("username", "madda")
            .put("port", 22)));
        Assert.assertNull(GhostexMachine.fromJson(new org.json.JSONObject()
            .put("id", "machine-2")
            .put("host", "macbook;shutdown")
            .put("username", "madda")
            .put("port", 22)));
        Assert.assertNull(GhostexMachine.fromJson(new org.json.JSONObject()
            .put("id", "machine-3")
            .put("host", "mac.tailnet.ts.net")
            .put("username", "madda$USER")
            .put("port", 22)));
        Assert.assertNull(GhostexMachine.fromJson(new org.json.JSONObject()
            .put("id", "machine-4")
            .put("host", "mac.tailnet.ts.net")
            .put("username", "madda")
            .put("port", 0)));
        Assert.assertNull(GhostexMachine.fromJson(new org.json.JSONObject()
            .put("id", "machine-5")
            .put("host", "mac.tailnet.ts.net")
            .put("username", "madda")
            .put("port", "ssh")));
        Assert.assertNull(GhostexMachine.fromJson(new org.json.JSONObject()
            .put("id", "machine/6")
            .put("host", "mac.tailnet.ts.net")
            .put("username", "madda")
            .put("port", 22)));
    }

    @Test
    public void fromJsonKeepsMissingPortBackwardCompatibleAsSshDefault() throws Exception {
        GhostexMachine machine = GhostexMachine.fromJson(new org.json.JSONObject()
            .put("id", "machine-1")
            .put("host", "mac.tailnet.ts.net")
            .put("username", "madda"));

        Assert.assertNotNull(machine);
        Assert.assertEquals(22, machine.port);
    }

}
