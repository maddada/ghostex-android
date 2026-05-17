package com.termux.app.ghostex;

import android.app.Activity;
import android.content.Intent;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class GhostexTailscaleLauncherTest {

    /*
    CDXC:AndroidConnectionRecovery 2026-05-17-15:12:
    Open Tailscale must remain a reliable recovery action even on Android
    images without the app or Play Store installed. Keep the package id and
    fallback destinations pinned so onboarding and reconnect repair do not
    drift into a broad package-query or crash-prone launcher path.

    CDXC:AndroidConnectionRecovery 2026-05-17-17:24:
    Launcher failures from store/browser intents should be recoverable status
    updates, not app crashes. Pin that runtime and security launch exceptions
    return false so the controller can keep recovery copy visible.
    */

    @Test
    public void usesNarrowTailscalePackageName() {
        Assert.assertEquals("com.tailscale.ipn", GhostexTailscaleLauncher.PACKAGE_NAME);
    }

    @Test
    public void marketFallbackTargetsTailscalePackage() {
        Intent intent = GhostexTailscaleLauncher.marketIntent();

        Assert.assertEquals(Intent.ACTION_VIEW, intent.getAction());
        Assert.assertEquals("market://details?id=com.tailscale.ipn", intent.getDataString());
    }

    @Test
    public void webFallbackTargetsTailscalePackage() {
        Intent intent = GhostexTailscaleLauncher.webIntent();

        Assert.assertEquals(Intent.ACTION_VIEW, intent.getAction());
        Assert.assertEquals("https://play.google.com/store/apps/details?id=com.tailscale.ipn", intent.getDataString());
    }

    @Test
    public void launchReturnsFalseWhenFallbackIntentsThrowRuntimeFailures() {
        ThrowingActivity activity = Robolectric.buildActivity(ThrowingActivity.class).setup().get();
        activity.throwRuntime = true;

        Assert.assertFalse(GhostexTailscaleLauncher.launch(activity));
        Assert.assertEquals(2, activity.startAttempts);
    }

    @Test
    public void launchReturnsFalseWhenFallbackIntentsThrowSecurityFailures() {
        ThrowingActivity activity = Robolectric.buildActivity(ThrowingActivity.class).setup().get();
        activity.throwSecurity = true;

        Assert.assertFalse(GhostexTailscaleLauncher.launch(activity));
        Assert.assertEquals(2, activity.startAttempts);
    }

    public static final class ThrowingActivity extends Activity {
        int startAttempts;
        boolean throwRuntime;
        boolean throwSecurity;

        @Override
        public void startActivity(Intent intent) {
            startAttempts++;
            if (throwSecurity) throw new SecurityException("blocked");
            if (throwRuntime) throw new RuntimeException("no handler");
        }
    }

}
