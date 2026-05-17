package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexServiceNotificationFormatterTest {

    /*
    CDXC:AndroidReleaseSurface 2026-05-17-13:19:
    Notification copy should stay Ghostex-specific even though Termux still
    owns the underlying foreground service implementation.
    */
    @Test
    public void formatsGhostexRemoteAttachState() {
        Assert.assertEquals("Ready to connect to Ghostex sessions",
            GhostexServiceNotificationFormatter.buildText(0, 0, false));
        Assert.assertEquals("1 remote terminal",
            GhostexServiceNotificationFormatter.buildText(1, 0, false));
        Assert.assertEquals("2 remote terminals, 1 background operation",
            GhostexServiceNotificationFormatter.buildText(2, 1, false));
        Assert.assertEquals("1 remote terminal · keeping connection awake",
            GhostexServiceNotificationFormatter.buildText(1, 0, true));
    }

}
