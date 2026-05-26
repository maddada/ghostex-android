package com.termux.app.ghostex;

public final class GhostexSessionStatusPollPolicy {

    /*
    CDXC:AndroidNotifications 2026-05-26-14:42:
    Android foreground notification rows should reflect remote ZMX session status within five seconds even when the sidebar is not visible. Keep this as the single poll interval source so controller scheduling and tests stay aligned with the notification freshness requirement.
    */
    public static final long MAX_STATUS_DELAY_MS = 5_000L;

    private GhostexSessionStatusPollPolicy() {}

    public static boolean shouldPoll(boolean controllerDestroyed, boolean tutorialSeen, boolean hasSelectedMachine) {
        return !controllerDestroyed && tutorialSeen && hasSelectedMachine;
    }

}
