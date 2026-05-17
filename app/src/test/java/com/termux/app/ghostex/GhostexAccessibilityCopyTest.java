package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexAccessibilityCopyTest {

    /*
    CDXC:AndroidSidebar 2026-05-17-16:12:
    Custom drawer and action-sheet rows should announce concise target/action
    descriptions without duplicate punctuation from visible copy fragments.
    */
    @Test
    public void joinSkipsBlankValuesAndAvoidsDuplicatePunctuation() {
        Assert.assertEquals("Connection failed. Open Tailscale. Tap to run.",
            GhostexAccessibilityCopy.join("Connection failed.", "", null, "Open Tailscale.", "Tap to run."));
    }

    @Test
    public void joinAddsSentenceBreaksBetweenFragments() {
        Assert.assertEquals("Kill. Stop this remote session. Destructive action. Tap to run.",
            GhostexAccessibilityCopy.join("Kill", "Stop this remote session", "Destructive action. Tap to run."));
    }

}
