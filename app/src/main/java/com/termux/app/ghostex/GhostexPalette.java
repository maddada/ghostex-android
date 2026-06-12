package com.termux.app.ghostex;

public final class GhostexPalette {

    /*
    CDXC:AndroidSidebar 2026-05-17-19:54:
    Ghostex Android's drawer, dialogs, machine switcher, and session rows should
    share the macOS sidebar palette. Keep these as literal ARGB ints so plain
    JVM unit tests can load UI helpers without Android framework Color static
    calls during class initialization.

    CDXC:AndroidSidebar 2026-05-17-20:08:
    The macOS sidebar reference is a neutral dark surface (`#181818` app shell
    and `#0e0e0e` modal shell), not a blue-slate theme. Keep Android drawer
    chrome neutral and reserve color for focus, status, and destructive accents
    so the mobile app matches the current macOS sidebar aesthetic.
    */
    public static final int BACKGROUND = 0xFF181818;
    public static final int FOREGROUND = 0xFFFAFAFA;
    public static final int MUTED = 0xFFB5B5B5;
    public static final int BORDER = 0x33FFFFFF;
    public static final int CARD = 0xFF1F1F1F;
    public static final int CARD_ACTIVE = 0xFF262626;
    public static final int INPUT_BACKGROUND = 0xFF0E0E0E;
    public static final int BUTTON = 0xFF7DD3FC;
    public static final int DANGER = 0xFFE85C5C;
    /*
    CDXC:AndroidStatusIndicators 2026-06-12-02:32:
    Done and attention status must use #95d7f6 instead of bright green so Android drawer rows match macOS and iOS status surfaces.
    */
    public static final int STATUS_ATTENTION = 0xFF95D7F6;
    public static final int STATUS_WORKING = 0xFFF59E0B;
    public static final int STATUS_SLEEPING = 0xFF6E7684;

    private GhostexPalette() {}

}
