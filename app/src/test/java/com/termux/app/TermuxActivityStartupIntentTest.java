package com.termux.app;

import android.content.Intent;

import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class TermuxActivityStartupIntentTest {

    /*
    CDXC:AndroidStartupRecovery 2026-06-13-01:42:
    Failsafe launch intents are the out-of-app recovery route when saved SSH startup state closes the normal Ghostex Android path.
    Keep both the Ghostex package extra and the legacy Termux extra recognized so adb/support instructions can recover users across package-id changes.
    */
    @Test
    public void shouldLaunchFailsafeSessionForGhostexExtra() {
        Intent intent = new Intent().putExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, true);

        Assert.assertTrue(TermuxActivity.shouldLaunchFailsafeSession(intent));
    }

    @Test
    public void shouldLaunchFailsafeSessionForLegacyTermuxExtra() {
        Intent intent = new Intent().putExtra(TermuxActivity.LEGACY_TERMUX_EXTRA_FAILSAFE_SESSION, true);

        Assert.assertTrue(TermuxActivity.shouldLaunchFailsafeSession(intent));
    }

    @Test
    public void shouldNotLaunchFailsafeSessionWithoutExplicitExtra() {
        Assert.assertFalse(TermuxActivity.shouldLaunchFailsafeSession(new Intent()));
        Assert.assertFalse(TermuxActivity.shouldLaunchFailsafeSession(null));
    }
}
