package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class GhostexMachineManagementPolicy {

    private GhostexMachineManagementPolicy() {}

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:34:
    Settings is a multi-machine account manager. Deleting a secondary saved
    machine must not disrupt the active SSH account; reconnect only when the
    deleted machine was the selected target.
    */
    public static boolean shouldReconnectAfterDelete(@NonNull String deletedMachineId,
                                                     @Nullable String selectedMachineId) {
        return deletedMachineId.equals(selectedMachineId);
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:37:
    Password changes made from Settings are credential-management edits, not
    account-selection actions. Reconnect is reserved for explicit Connect,
    recovery retry, and startup restore flows.
    */
    public static boolean shouldReconnectAfterSettingsPasswordSave() {
        return false;
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:40:
    Credential prompts are asynchronous UI surfaces. If the target machine was
    deleted before the user accepts the prompt, the prompt must not save
    credentials or recreate that account.
    */
    public static boolean canAcceptCredentialPromptForExistingMachine(boolean machineStillExists) {
        return machineStillExists;
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:40:
    Machine-scoped Settings actions are allowed only while their saved machine
    still exists. Stale edit/action/repair dialogs should expire instead of
    mutating or reconnecting a different account.

    CDXC:AndroidConnectionManagement 2026-05-17-15:23:
    Stable machine ids survive host/user/port edits. A machine-scoped dialog
    opened before that edit should expire unless the saved target still matches,
    otherwise old recovery UI can save credentials or reset the saved SSHJ host
    key for the wrong Mac under the edited account.
    */
    public static boolean canRunExistingMachineAction(boolean machineStillExistsAndTargetMatches) {
        return machineStillExistsAndTargetMatches;
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-15:52:
    Destructive confirmations can remain open while a saved machine is deleted
    or edited under the same stable id. Re-check existence and SSH target match
    at accept time so stale delete confirmations cannot remove the newly edited
    account behind that id.
    */
    public static boolean canAcceptDestructiveMachineConfirmation(boolean machineStillExistsAndTargetMatches) {
        return canRunExistingMachineAction(machineStillExistsAndTargetMatches);
    }

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-16:04:
    Session/project action sheets are opened from the currently selected remote
    sidebar. Their callbacks must stay bound to that opener machine and expire
    after machine switches or same-id target edits instead of running the saved
    session id against a different Mac.
    */
    public static boolean canRunRemoteSidebarAction(boolean machineStillSelectedAndTargetMatches) {
        return machineStillSelectedAndTargetMatches;
    }

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-15:19:
    Warm attach terminals are bound to the SSH target behind a saved machine
    id. Editing host, username, or port keeps the same id for settings
    continuity, but must drop old warm terminals so a later tap cannot reuse an
    SSH session connected to the previous Mac.
    */
    public static boolean shouldEvictWarmSessionsAfterTargetEdit(boolean targetChanged) {
        return targetChanged;
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-15:21:
    Non-destructive checks and selected-machine reconnect/action callbacks are
    scoped by machine id plus generation. Host/user/port edits keep the same
    machine id, so target edits must advance generations to expire work that
    started against the previous SSH target.
    */
    public static boolean shouldInvalidateRequestsAfterTargetEdit(boolean targetChanged) {
        return targetChanged;
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-21:05:
    Reconnect success is an asynchronous write into the saved-machine manager.
    Rebase the Last Connected timestamp on the current saved record instead of
    the request snapshot so Settings edits made during SSH work are not rolled
    back by a late success callback.
    */
    @Nullable
    public static GhostexMachine connectedMachineRecord(@Nullable GhostexMachine currentMatchingMachine,
                                                        long connectedAtMs) {
        return currentMatchingMachine == null ? null : currentMatchingMachine.withLastConnectedAt(connectedAtMs);
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-21:33:
    The drawer machine spinner is both a touch control and an accessibility
    control. Ignore programmatic reselection of the already active machine, but
    allow keyboard and screen-reader selection to switch accounts or open Add
    SSH machine without requiring a touch-down marker.
    */
    public static boolean shouldHandleMachineSpinnerMachineSelection(boolean touchStartedSelection,
                                                                    boolean selectedMachineAlreadyActive) {
        return touchStartedSelection || !selectedMachineAlreadyActive;
    }

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-15:40:
    Session-only SSH passwords live only in process memory, but the cache still
    needs the same machine-id cleanup as the encrypted vault. Once a machine is
    deleted or scrubbed by self-healing, its in-memory password is no longer
    attached to a valid reconnect target.
    */
    public static List<String> orphanSessionPasswordMachineIds(@NonNull Collection<String> cachedMachineIds,
                                                               @NonNull Collection<String> savedMachineIds) {
        ArrayList<String> orphanIds = new ArrayList<>();
        for (String machineId : cachedMachineIds) {
            if (!savedMachineIds.contains(machineId)) orphanIds.add(machineId);
        }
        return orphanIds;
    }

}
