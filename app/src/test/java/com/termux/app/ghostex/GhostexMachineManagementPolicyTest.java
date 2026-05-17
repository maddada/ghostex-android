package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class GhostexMachineManagementPolicyTest {

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:34:
    Machine deletion should preserve the active account unless the user deleted
    that active account. This keeps Settings safe for managing multiple Macs
    without surprise reconnects.
    */
    @Test
    public void reconnectsOnlyWhenDeletingSelectedMachine() {
        Assert.assertTrue(GhostexMachineManagementPolicy.shouldReconnectAfterDelete("mac-a", "mac-a"));
        Assert.assertFalse(GhostexMachineManagementPolicy.shouldReconnectAfterDelete("mac-b", "mac-a"));
        Assert.assertFalse(GhostexMachineManagementPolicy.shouldReconnectAfterDelete("mac-b", null));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:37:
    Saving a password from Settings is intentionally safe for secondary
    machines. It must not update the selected account or reconnect until the
    user presses Connect.
    */
    @Test
    public void settingsPasswordSaveDoesNotReconnect() {
        Assert.assertFalse(GhostexMachineManagementPolicy.shouldReconnectAfterSettingsPasswordSave());
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:40:
    A stale credential prompt must expire after its target machine is deleted,
    otherwise accepting the prompt can recreate a removed account.
    */
    @Test
    public void credentialPromptRequiresExistingMachine() {
        Assert.assertTrue(GhostexMachineManagementPolicy.canAcceptCredentialPromptForExistingMachine(true));
        Assert.assertFalse(GhostexMachineManagementPolicy.canAcceptCredentialPromptForExistingMachine(false));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:40:
    Stale machine action surfaces should expire after deletion instead of
    mutating the deleted record or reconnecting a fallback account.

    CDXC:AndroidConnectionManagement 2026-05-17-15:23:
    They should also expire after host/user/port edits because the stable
    machine id now points at a different SSH target.
    */
    @Test
    public void existingMachineActionsRequireExistingMachine() {
        Assert.assertTrue(GhostexMachineManagementPolicy.canRunExistingMachineAction(true));
        Assert.assertFalse(GhostexMachineManagementPolicy.canRunExistingMachineAction(false));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-15:52:
    Destructive confirmations need the same target freshness guard as action
    sheets, because a delete confirmation can outlive a same-id SSH target edit.
    */
    @Test
    public void destructiveConfirmationsRequireCurrentMachineTarget() {
        Assert.assertTrue(GhostexMachineManagementPolicy.canAcceptDestructiveMachineConfirmation(true));
        Assert.assertFalse(GhostexMachineManagementPolicy.canAcceptDestructiveMachineConfirmation(false));
    }

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-16:04:
    Remote sidebar actions capture a session/project row from one selected
    machine. They must expire after a machine switch or target edit instead of
    running that row against whatever account is selected later.
    */
    @Test
    public void remoteSidebarActionsRequireSelectedCurrentTarget() {
        Assert.assertTrue(GhostexMachineManagementPolicy.canRunRemoteSidebarAction(true));
        Assert.assertFalse(GhostexMachineManagementPolicy.canRunRemoteSidebarAction(false));
    }

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-15:19:
    Editing a saved machine's SSH target keeps the machine id, so warm attach
    terminals must be evicted on target changes to avoid reconnecting users to
    the previous Mac under the edited account.
    */
    @Test
    public void targetEditsEvictWarmSessions() {
        Assert.assertTrue(GhostexMachineManagementPolicy.shouldEvictWarmSessionsAfterTargetEdit(true));
        Assert.assertFalse(GhostexMachineManagementPolicy.shouldEvictWarmSessionsAfterTargetEdit(false));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-15:21:
    A target edit must also expire pending async work because the machine id is
    intentionally stable while the SSH endpoint behind it changes.
    */
    @Test
    public void targetEditsInvalidateAsyncRequests() {
        Assert.assertTrue(GhostexMachineManagementPolicy.shouldInvalidateRequestsAfterTargetEdit(true));
        Assert.assertFalse(GhostexMachineManagementPolicy.shouldInvalidateRequestsAfterTargetEdit(false));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-21:05:
    A late reconnect success should update Last Connected on the latest saved
    machine record without rolling back display-name or password-mode edits
    made in Settings while SSH was still running.
    */
    @Test
    public void reconnectSuccessRebasesLastConnectedOnCurrentMachineRecord() {
        GhostexMachine current = new GhostexMachine("mac-a", "Renamed Mac",
            "mac-a.tailnet.ts.net", "madda", 22, true, 100L);

        GhostexMachine connected = GhostexMachineManagementPolicy.connectedMachineRecord(current, 200L);

        Assert.assertNotNull(connected);
        Assert.assertEquals("Renamed Mac", connected.name);
        Assert.assertTrue(connected.savePassword);
        Assert.assertEquals(200L, connected.lastConnectedAt);
    }

    @Test
    public void reconnectSuccessSkipsWriteWhenMachineNoLongerMatches() {
        Assert.assertNull(GhostexMachineManagementPolicy.connectedMachineRecord(null, 200L));
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-21:33:
    Machine spinner selection should suppress only non-touch reselection of the
    already active account. Different-account switching and Add SSH machine
    recovery must remain reachable from keyboard and screen-reader selection.
    */
    @Test
    public void spinnerMachineSelectionAllowsTouchOrNewActiveTarget() {
        Assert.assertFalse(GhostexMachineManagementPolicy.shouldHandleMachineSpinnerMachineSelection(false, true));
        Assert.assertTrue(GhostexMachineManagementPolicy.shouldHandleMachineSpinnerMachineSelection(true, true));
        Assert.assertTrue(GhostexMachineManagementPolicy.shouldHandleMachineSpinnerMachineSelection(false, false));
    }

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-15:40:
    Session-only password memory should be pruned against the cleaned machine
    list so deleted or self-healed-away accounts do not keep process-only
    credentials until controller teardown.
    */
    @Test
    public void detectsOrphanSessionOnlyPasswordIds() {
        List<String> orphanIds = GhostexMachineManagementPolicy.orphanSessionPasswordMachineIds(
            Arrays.asList("mac-a", "deleted", "mac-b"),
            Arrays.asList("mac-a", "mac-b"));

        Assert.assertEquals(1, orphanIds.size());
        Assert.assertEquals("deleted", orphanIds.get(0));
    }

}
