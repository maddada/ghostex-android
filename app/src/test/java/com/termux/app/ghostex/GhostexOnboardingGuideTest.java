package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexOnboardingGuideTest {

    /*
    CDXC:AndroidOnboarding 2026-05-17-16:58:
    First-run onboarding must be complete enough for a release user to set up
    the Mac and phone without guessing. Pin the exact tutorial contract so
    future UI refactors cannot drop Tailscale, Remote Login, Ghostex CLI, zmx,
    built-in SSH transport, saved-machine setup, reconnect, or the
    SSH/CLI/ZMX model.
    */
    @Test
    public void tutorialExplainsFullMacPhoneConnectionSetup() {
        String tutorial = GhostexOnboardingGuide.TITLE + "\n" +
            GhostexOnboardingGuide.INTRO + "\n" +
            String.join("\n", GhostexOnboardingGuide.steps());

        Assert.assertTrue(tutorial.contains("Tailscale"));
        Assert.assertTrue(tutorial.contains("Remote Login"));
        Assert.assertTrue(tutorial.contains("command -v ghostex && command -v zmx"));
        Assert.assertTrue(tutorial.contains("Session persistence to zmx"));
        Assert.assertTrue(tutorial.contains("built-in Setup panel"));
        Assert.assertTrue(tutorial.contains("You do not need to install SSH packages on the phone."));
	        Assert.assertTrue(tutorial.contains("display name, host, username, and SSH port"));
	        Assert.assertTrue(tutorial.contains("reconnect to the last selected machine"));
	        Assert.assertTrue(tutorial.contains("asks the Ghostex CLI for the GX server-backed session list"));
	        Assert.assertTrue(tutorial.contains("macOS app does not need to stay open"));
	        Assert.assertTrue(tutorial.contains("ZMX-backed session"));
    }

    @Test
    public void stepsReturnDefensiveCopy() {
        String[] steps = GhostexOnboardingGuide.steps();
        steps[0] = "changed";

        Assert.assertNotEquals("changed", GhostexOnboardingGuide.steps()[0]);
    }

}
