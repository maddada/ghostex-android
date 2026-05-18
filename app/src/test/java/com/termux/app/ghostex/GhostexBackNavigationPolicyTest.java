package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexBackNavigationPolicyTest {

    /*
    CDXC:AndroidNavigation 2026-05-18-04:43:
    Android back gestures are always sidebar shortcuts for Ghostex Android.
    They must never exit the app; users leave from the explicit drawer button.
    */
    @Test
    public void neverExitsFromBackShortcut() {
        Assert.assertFalse(GhostexBackNavigationPolicy.shouldExit(0L, 10_000L));
        Assert.assertFalse(GhostexBackNavigationPolicy.shouldExit(10_000L, 15_001L));
        Assert.assertFalse(GhostexBackNavigationPolicy.shouldExit(10_000L, 15_000L));
        Assert.assertFalse(GhostexBackNavigationPolicy.shouldExit(10_000L, 12_000L));
    }

}
