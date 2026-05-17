package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexBackNavigationPolicyTest {

    /*
    CDXC:AndroidNavigation 2026-05-18-01:27:
    The first Android back gesture is a sidebar shortcut for Ghostex Android;
    only a second back gesture inside five seconds should exit the app.
    */
    @Test
    public void exitsOnlyAfterSecondBackInsideWindow() {
        Assert.assertFalse(GhostexBackNavigationPolicy.shouldExit(0L, 10_000L));
        Assert.assertFalse(GhostexBackNavigationPolicy.shouldExit(10_000L, 15_001L));
        Assert.assertTrue(GhostexBackNavigationPolicy.shouldExit(10_000L, 15_000L));
        Assert.assertTrue(GhostexBackNavigationPolicy.shouldExit(10_000L, 12_000L));
    }

}
