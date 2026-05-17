package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class GhostexAsyncRequestGuard {

    private GhostexAsyncRequestGuard() {}

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-20:06:
    Saved-machine switching can leave older SSH reconnects or remote actions in
    flight. Only the latest reconnect generation for the currently selected
    machine may mutate the drawer, status, or password prompts.

    CDXC:AndroidConnectionManagement 2026-05-17-13:52:
    Saved-machine checks are non-destructive but still asynchronous. Only the
    latest check request should update status, open phone setup, or prompt for
    credentials so a slow readiness probe from another machine cannot overwrite
    the current recovery flow.
    */
    public static boolean isCurrentReconnect(long requestGeneration,
                                             long currentGeneration,
                                             @NonNull String requestMachineId,
                                             @Nullable String selectedMachineId) {
        return requestGeneration == currentGeneration && requestMachineId.equals(selectedMachineId);
    }

    public static boolean isCurrentMachine(@NonNull String requestMachineId,
                                           @Nullable String selectedMachineId) {
        return requestMachineId.equals(selectedMachineId);
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:47:
    Machine id alone is not enough for long SSH work because users can switch
    A -> B -> A before an older action returns. Pair machine id with a request
    generation so stale attach/action callbacks expire after any reconnect or
    newer action changes the visible remote-session context.
    */
    public static boolean isCurrentMachineRequest(long requestGeneration,
                                                  long currentGeneration,
                                                  @NonNull String requestMachineId,
                                                  @Nullable String selectedMachineId) {
        return requestGeneration == currentGeneration && requestMachineId.equals(selectedMachineId);
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-14:52:
    Activity teardown can race with queued SSH callbacks. A request may match
    the latest machine/generation and still be unsafe to apply after the
    controller is destroyed, so lifecycle state participates in async guards.
    */
    public static boolean isLiveMachineRequest(boolean controllerAlive,
                                               long requestGeneration,
                                               long currentGeneration,
                                               @NonNull String requestMachineId,
                                               @Nullable String selectedMachineId) {
        return controllerAlive &&
            isCurrentMachineRequest(requestGeneration, currentGeneration, requestMachineId, selectedMachineId);
    }

    public static boolean isLatestRequest(long requestGeneration,
                                          long currentGeneration) {
        return requestGeneration == currentGeneration;
    }

}
