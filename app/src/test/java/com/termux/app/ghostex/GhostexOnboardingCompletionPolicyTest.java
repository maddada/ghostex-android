package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexOnboardingCompletionPolicyTest {

    /*
    CDXC:AndroidOnboarding 2026-05-17-19:11:
    The required first-run tutorial must hand users into machine setup without
    treating a dismissed or canceled editor as a completed app setup. Pin that
    distinction separately from the Activity so the release UX cannot regress
    into a tutorial that disappears before any SSH target exists.
    */
    @Test
    public void requiredFirstRunNeedsSavedMachineBeforeTutorialCanBeSeen() {
        Assert.assertFalse(GhostexOnboardingCompletionPolicy
            .shouldMarkTutorialSeenFromTutorialAction(true, false));
        Assert.assertTrue(GhostexOnboardingCompletionPolicy
            .shouldMarkTutorialSeenFromTutorialAction(true, true));
    }

    @Test
    public void informationalTutorialCanBeDismissedWithoutMachineSetup() {
        Assert.assertTrue(GhostexOnboardingCompletionPolicy
            .shouldMarkTutorialSeenFromTutorialAction(false, false));
    }

    @Test
    public void firstMachineSaveCompletesUnseenOnboarding() {
        Assert.assertTrue(GhostexOnboardingCompletionPolicy
            .shouldMarkTutorialSeenAfterMachineSave(false, true));
        Assert.assertFalse(GhostexOnboardingCompletionPolicy
            .shouldMarkTutorialSeenAfterMachineSave(false, false));
        Assert.assertFalse(GhostexOnboardingCompletionPolicy
            .shouldMarkTutorialSeenAfterMachineSave(true, true));
    }
}
