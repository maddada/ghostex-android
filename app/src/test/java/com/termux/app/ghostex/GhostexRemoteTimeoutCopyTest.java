package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexRemoteTimeoutCopyTest {

    /*
    CDXC:AndroidConnectionRecovery 2026-05-17-19:07:
    Timeout failures do not include stderr for summarizeFailure to map.
    Test the dedicated copy helper so reconnect, readiness checks, rename, and
    session actions all stay actionable when Tailscale or SSH is slow to answer.
    */
    @Test
    public void timeoutCopyMentionsTailscaleRetryAndInterruptedIntent() {
        GhostexMachine machine = new GhostexMachine("mac-1", "Mac Studio", "mac.tailnet.ts.net", "madda", 22, true, 0L);

        String connecting = GhostexRemoteTimeoutCopy.connecting(machine);
        Assert.assertTrue(connecting.contains("connecting to Mac Studio"));
        Assert.assertTrue(connecting.contains("Open Tailscale"));
        Assert.assertTrue(connecting.contains("retry"));

        String checking = GhostexRemoteTimeoutCopy.checking(machine);
        Assert.assertTrue(checking.contains("checking Mac Studio"));
        Assert.assertTrue(checking.contains("Open Tailscale"));
        Assert.assertTrue(checking.contains("retry"));

        String action = GhostexRemoteTimeoutCopy.sessionAction("wake");
        Assert.assertTrue(action.contains("ghostex wake"));
        Assert.assertTrue(action.contains("Open Tailscale"));
        Assert.assertTrue(action.contains("retry"));

        String rename = GhostexRemoteTimeoutCopy.renaming();
        Assert.assertTrue(rename.contains("renaming this session"));
        Assert.assertTrue(rename.contains("Open Tailscale"));
        Assert.assertTrue(rename.contains("retry"));
    }
}
