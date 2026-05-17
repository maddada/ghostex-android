package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexConnectionRecoveryTest {

    /*
    CDXC:AndroidConnectionRecovery 2026-05-17-13:33:
    Credential failures can appear during reconnect or later remote actions.
    Keep the password-prompt trigger testable so each SSH workflow can share
    the same recovery path.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:04:
    Check connection should recognize host-key repair messages separately from
    credentials so Android can show the confirmed known_hosts reset for the
    checked machine.
    */

    @Test
    public void promptsForMissingPasswordMessages() {
        Assert.assertTrue(GhostexConnectionRecovery.shouldPromptForPassword(
            "SSH needs a key or password. Open the machine settings and save a password, or configure SSH keys/Tailscale SSH."));
    }

    @Test
    public void promptsForRejectedSavedPasswordMessages() {
        Assert.assertTrue(GhostexConnectionRecovery.shouldPromptForPassword(
            "SSH rejected the saved password. Update the saved machine password or choose another machine."));
    }

    @Test
    public void ignoresNonCredentialRecoveryMessages() {
        Assert.assertFalse(GhostexConnectionRecovery.shouldPromptForPassword(
            "Could not reach the machine. Open Tailscale and confirm both devices are online."));
        Assert.assertFalse(GhostexConnectionRecovery.shouldPromptForPassword(null));
    }

    @Test
    public void promptsForHostKeyResetMessages() {
        Assert.assertTrue(GhostexConnectionRecovery.shouldPromptForHostKeyReset(
            "SSH host key verification failed. Open Setup and remove the old known_hosts entry for this machine, or confirm you are connecting to the right Mac."));
    }

    @Test
    public void ignoresNonHostKeyResetMessages() {
        Assert.assertFalse(GhostexConnectionRecovery.shouldPromptForHostKeyReset(
            "SSH rejected the saved password. Update the saved machine password or choose another machine."));
        Assert.assertFalse(GhostexConnectionRecovery.shouldPromptForHostKeyReset(null));
    }

}
