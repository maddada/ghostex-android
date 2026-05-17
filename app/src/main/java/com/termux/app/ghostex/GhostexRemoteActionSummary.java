package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GhostexRemoteActionSummary {

    private final ArrayList<GhostexRemoteSession> successfulSessions = new ArrayList<>();
    private final ArrayList<GhostexRemoteSession> failedSessions = new ArrayList<>();
    private String lastErrorMessage;
    private String credentialErrorMessage;

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-12:56:
    Project-level actions can partially succeed over SSH. Track successful rows
    separately from the final error so Android can evict stale warm terminals
    for sessions that were actually slept/killed and still report the failure
    that needs user attention.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:05:
    A project action retry after password recovery should target only failed
    rows. Keep failed-session and credential-error evidence in the summary so
    Android does not repeat lifecycle actions against sessions that already
    changed on the Mac.
    */
    public void record(@NonNull GhostexRemoteSession session,
                       @NonNull GhostexSessionInventoryClient.Result result) {
        if (result.ok) {
            successfulSessions.add(session);
        } else {
            failedSessions.add(session);
            lastErrorMessage = result.errorMessage;
            if (credentialErrorMessage == null &&
                GhostexConnectionRecovery.shouldPromptForPassword(result.errorMessage)) {
                credentialErrorMessage = result.errorMessage;
            }
        }
    }

    public boolean hasSuccesses() {
        return !successfulSessions.isEmpty();
    }

    public boolean hasFailures() {
        return lastErrorMessage != null;
    }

    @Nullable
    public String lastErrorMessage() {
        return lastErrorMessage;
    }

    @Nullable
    public String credentialErrorMessage() {
        return credentialErrorMessage;
    }

    @NonNull
    public List<GhostexRemoteSession> successfulSessions() {
        return new ArrayList<>(successfulSessions);
    }

    @NonNull
    public List<GhostexRemoteSession> failedSessions() {
        return new ArrayList<>(failedSessions);
    }

}
