package com.termux.app.ghostex;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class GhostexServiceNotificationFormatter {

    private GhostexServiceNotificationFormatter() {}

    /*
    CDXC:AndroidReleaseSurface 2026-05-17-13:19:
    The foreground notification is a user-visible release surface. Describe
    Termux sessions as Ghostex remote attach terminals and background work as
    Ghostex operations so the Android app does not read like a stock local
    Termux session manager.
    */
    public static String buildText(int remoteAttachCount, int backgroundOperationCount,
                                   boolean keepAwakeEnabled) {
        StringBuilder text = new StringBuilder();
        if (remoteAttachCount <= 0) {
            text.append("Ready to connect to Ghostex sessions");
        } else {
            text.append(remoteAttachCount)
                .append(remoteAttachCount == 1 ? " remote terminal" : " remote terminals");
        }

        if (backgroundOperationCount > 0) {
            text.append(", ")
                .append(backgroundOperationCount)
                .append(backgroundOperationCount == 1 ? " background operation" : " background operations");
        }

        if (keepAwakeEnabled) text.append(" · keeping connection awake");
        return text.toString();
    }

    /*
    CDXC:AndroidNotifications 2026-05-21-22:57:
    Android's notification shade starts from the Mac inventory order, which is
    already the latest-session order. Keep that source order stable within each
    notification priority bucket so Android rows remain predictable.

    CDXC:AndroidNotifications 2026-05-23-14:40:
    Android notification rows must make the same working state visible that the
    macOS desktop status indicator shows. Prioritize done/attention rows first,
    then working rows, then ordinary running rows while preserving Mac order
    within each state bucket so actionable session indicators are not hidden
    below newer idle sessions.
    */
    public static ArrayList<GhostexRemoteSession> sortForNotification(@NonNull List<GhostexRemoteSession> sessions) {
        ArrayList<GhostexRemoteSession> sortedSessions = new ArrayList<>(sessions);
        Collections.sort(sortedSessions, Comparator.comparingInt(GhostexServiceNotificationFormatter::notificationPriority));
        return sortedSessions;
    }

    private static int notificationPriority(@NonNull GhostexRemoteSession session) {
        if (isDone(session)) return 0;
        if (isWorking(session)) return 1;
        return 2;
    }

    public static int doneCount(@NonNull List<GhostexRemoteSession> sessions) {
        int count = 0;
        for (GhostexRemoteSession session : sessions) {
            if (isDone(session)) count++;
        }
        return count;
    }

    public static int workingCount(@NonNull List<GhostexRemoteSession> sessions) {
        int count = 0;
        for (GhostexRemoteSession session : sessions) {
            if (isWorking(session)) count++;
        }
        return count;
    }

    public static int runningCount(@NonNull List<GhostexRemoteSession> sessions) {
        int count = 0;
        for (GhostexRemoteSession session : sessions) {
            if (isRunning(session)) count++;
        }
        return count;
    }

    public static boolean isDone(@NonNull GhostexRemoteSession session) {
        String status = session.displayStatus();
        return "attention".equals(status) || "done".equals(status);
    }

    public static boolean isWorking(@NonNull GhostexRemoteSession session) {
        return "working".equals(session.displayStatus());
    }

    public static boolean isRunning(@NonNull GhostexRemoteSession session) {
        if (session.isSleeping || isDone(session) || isWorking(session)) return false;
        String status = session.status == null ? "" : session.status.trim().toLowerCase(Locale.US);
        String displayStatus = session.displayStatus();
        return "running".equals(status) || "idle".equals(displayStatus) || displayStatus.isEmpty();
    }

    public static StatusIndicator statusIndicator(@NonNull List<GhostexRemoteSession> sessions) {
        /*
        CDXC:AndroidNotifications 2026-05-21-23:52:
        Android can expose one notification count, so use the requested
        precedence: done first, then in-progress, then running. Keep the
        decision in this formatter so the service notification and tests cannot
        drift from the product rule.
        */
        int done = doneCount(sessions);
        if (done > 0) return new StatusIndicator("done", done);
        int working = workingCount(sessions);
        if (working > 0) return new StatusIndicator("working", working);
        return new StatusIndicator("running", runningCount(sessions));
    }

    public static final class StatusIndicator {
        public final String state;
        public final int count;

        private StatusIndicator(@NonNull String state, int count) {
            this.state = state;
            this.count = count;
        }
    }

}
