package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/*
CDXC:AndroidSidebar 2026-07-18:
The drawer stacks one section per saved machine, so the controller keeps the
latest fetched inventory per machine id instead of only the selected machine's
flat session list. A snapshot is either a successful sessions+workspace pair or
a short failure message that renders as that machine's in-section state card.
*/
final class GhostexMachineInventorySnapshot {

    @NonNull final GhostexMachine machine;
    @NonNull final List<GhostexRemoteSession> sessions;
    @Nullable final GhostexWorkspaceInventory workspace;
    @Nullable final String errorMessage;

    private GhostexMachineInventorySnapshot(@NonNull GhostexMachine machine,
                                            @NonNull List<GhostexRemoteSession> sessions,
                                            @Nullable GhostexWorkspaceInventory workspace,
                                            @Nullable String errorMessage) {
        this.machine = machine;
        this.sessions = sessions;
        this.workspace = workspace;
        this.errorMessage = errorMessage;
    }

    @NonNull
    static GhostexMachineInventorySnapshot success(@NonNull GhostexMachine machine,
                                                   @NonNull List<GhostexRemoteSession> sessions,
                                                   @Nullable GhostexWorkspaceInventory workspace) {
        return new GhostexMachineInventorySnapshot(machine, new ArrayList<>(sessions), workspace, null);
    }

    @NonNull
    static GhostexMachineInventorySnapshot failure(@NonNull GhostexMachine machine,
                                                   @Nullable String errorMessage) {
        return new GhostexMachineInventorySnapshot(machine, new ArrayList<>(),
            null, errorMessage == null || errorMessage.trim().isEmpty()
                ? "Could not load sessions from this machine."
                : errorMessage.trim());
    }

    boolean isSuccess() {
        return errorMessage == null;
    }

}
