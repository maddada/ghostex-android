package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexWarmSessionMetadataTest {

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-16:16:
    Warm attach metadata must round-trip through ExecutionCommand.commandLabel so
    Activity recreation can recover running SSH attach terminals from TermuxService.
    */
    @Test
    public void attachCommandLabelRoundTripsMachineAndSessionIds() {
        String label = GhostexWarmSessionMetadata.buildAttachCommandLabel("mac-main", "session:abc/123");

        GhostexWarmSessionMetadata.GhostexWarmSessionIds ids =
            GhostexWarmSessionMetadata.parseAttachCommandLabel(label);

        Assert.assertNotNull(ids);
        Assert.assertEquals("mac-main", ids.machineId);
        Assert.assertEquals("session:abc/123", ids.sessionId);
    }

    @Test
    public void parserRejectsNonGhostexOrMalformedLabels() {
        Assert.assertNull(GhostexWarmSessionMetadata.parseAttachCommandLabel("ssh mac"));
        Assert.assertNull(GhostexWarmSessionMetadata.parseAttachCommandLabel("ghostex-android-attach:bad"));
        Assert.assertNull(GhostexWarmSessionMetadata.parseAttachCommandLabel("ghostex-android-attach:zz:11"));
    }

}
