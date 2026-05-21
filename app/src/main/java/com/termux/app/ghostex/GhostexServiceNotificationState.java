package com.termux.app.ghostex;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class GhostexServiceNotificationState {

    private static final Object LOCK = new Object();
    private static ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();

    private GhostexServiceNotificationState() {}

    /*
    CDXC:AndroidNotifications 2026-05-21-23:02:
    The foreground service owns Android's notification, while
    GhostexAndroidController owns the remote ZMX inventory. Keep a small
    process-local snapshot so notification shade rows can show and activate the
    same sessions as the drawer without coupling TermuxService to drawer views.
    */
    public static void updateSessions(@NonNull List<GhostexRemoteSession> nextSessions) {
        synchronized (LOCK) {
            sessions = GhostexServiceNotificationFormatter.sortForNotification(nextSessions);
        }
    }

    public static void clearSessions() {
        synchronized (LOCK) {
            sessions = new ArrayList<>();
        }
    }

    @NonNull
    public static ArrayList<GhostexRemoteSession> sessions() {
        synchronized (LOCK) {
            return new ArrayList<>(sessions);
        }
    }

}
