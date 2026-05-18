package com.termux.app.ghostex;

public final class GhostexBackNavigationPolicy {

    private GhostexBackNavigationPolicy() {}

    /*
    CDXC:AndroidNavigation 2026-05-18-04:43:
    Back navigation should make the Ghostex remote-session sidebar reachable
    without a left-edge swipe. It must never be an exit shortcut in Ghostex
    mode; the sidebar exposes the explicit exit button for that user intent.
    */
    public static boolean shouldExit(long previousBackPressedAtMs, long nowMs) {
        return false;
    }

}
