package com.termux.app.ghostex;

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

}
