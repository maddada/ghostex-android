package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexPaletteTest {

    /*
    CDXC:AndroidSidebar 2026-05-17-19:54:
    The Android drawer, dialogs, session rows, and saved-machine switcher share
    the macOS sidebar palette through GhostexPalette. Keep this covered in a
    plain JVM test so palette constants remain literal ARGB values and do not
    reintroduce Android framework static initialization into unit-test helpers.

    CDXC:AndroidSidebar 2026-05-17-20:08:
    Pin the neutral macOS sidebar tokens so future Android polish does not drift
    back to a blue-slate drawer theme while chasing local Material defaults.
    */
    @Test
    public void exposesMacSidebarPaletteTokensAsLiteralArgbValues() {
        Assert.assertEquals(0xFF181818, GhostexPalette.BACKGROUND);
        Assert.assertEquals(0xFFFAFAFA, GhostexPalette.FOREGROUND);
        Assert.assertEquals(0xFF1F1F1F, GhostexPalette.CARD);
        Assert.assertEquals(0xFF262626, GhostexPalette.CARD_ACTIVE);
        Assert.assertEquals(0xFF0E0E0E, GhostexPalette.INPUT_BACKGROUND);
        Assert.assertEquals(0xFF7DD3FC, GhostexPalette.BUTTON);
        Assert.assertEquals(0xFFF59E0B, GhostexPalette.STATUS_WORKING);
    }

}
