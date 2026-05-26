package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexSessionStatusPollPolicyTest {

    /*
    CDXC:AndroidNotifications 2026-05-26-14:42:
    Android notification shade session statuses should update from the selected machine without requiring the sidebar to be visible, with a maximum normal poll delay of five seconds.
    */
    @Test
    public void pollsSelectedMachineWithinFiveSeconds() {
        Assert.assertEquals(5_000L, GhostexSessionStatusPollPolicy.MAX_STATUS_DELAY_MS);
        Assert.assertTrue(GhostexSessionStatusPollPolicy.shouldPoll(false, true, true));
    }

    @Test
    public void stopsPollingWhenControllerCannotRefreshInventory() {
        Assert.assertFalse(GhostexSessionStatusPollPolicy.shouldPoll(true, true, true));
        Assert.assertFalse(GhostexSessionStatusPollPolicy.shouldPoll(false, false, true));
        Assert.assertFalse(GhostexSessionStatusPollPolicy.shouldPoll(false, true, false));
    }

}
