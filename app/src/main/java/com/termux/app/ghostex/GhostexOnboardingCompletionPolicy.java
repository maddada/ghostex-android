package com.termux.app.ghostex;

public final class GhostexOnboardingCompletionPolicy {

    private GhostexOnboardingCompletionPolicy() {}

    /*
    CDXC:AndroidOnboarding 2026-05-17-19:11:
    First-run onboarding is complete only after the app can reconnect to a
    saved SSH machine. Let informational tutorial opens finish immediately, but
    keep a fresh install in onboarding until machine setup succeeds.
    */
    public static boolean shouldMarkTutorialSeenFromTutorialAction(boolean requiredFirstRun,
                                                                   boolean hasSavedMachine) {
        return !requiredFirstRun || hasSavedMachine;
    }

    public static boolean shouldMarkTutorialSeenAfterMachineSave(boolean tutorialAlreadySeen,
                                                                 boolean hasSavedMachineAfterSave) {
        return !tutorialAlreadySeen && hasSavedMachineAfterSave;
    }
}
