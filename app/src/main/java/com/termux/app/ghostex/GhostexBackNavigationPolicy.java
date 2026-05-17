package com.termux.app.ghostex;

public final class GhostexBackNavigationPolicy {

    public static final long EXIT_WINDOW_MS = 5_000L;

    private GhostexBackNavigationPolicy() {}

    /*
    CDXC:AndroidNavigation 2026-05-18-01:27:
    Back navigation should make the Ghostex remote-session sidebar reachable
    without a left-edge swipe. Treat the first back gesture as sidebar access,
    and only exit after a second back gesture inside a five-second window.
    */
    public static boolean shouldExit(long previousBackPressedAtMs, long nowMs) {
        return previousBackPressedAtMs > 0L && nowMs - previousBackPressedAtMs <= EXIT_WINDOW_MS;
    }

}
