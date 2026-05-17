package com.termux.app.ghostex;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

public final class GhostexTailscaleLauncher {

    public static final String PACKAGE_NAME = "com.tailscale.ipn";
    static final String MARKET_URI = "market://details?id=" + PACKAGE_NAME;
    static final String WEB_URI = "https://play.google.com/store/apps/details?id=" + PACKAGE_NAME;

    private GhostexTailscaleLauncher() {}

    /*
    CDXC:AndroidConnectionRecovery 2026-05-17-15:12:
    Tailscale is a primary recovery action from onboarding, setup, and failed
    reconnect states. Try the installed app first, then Play Store, then the
    web listing, and report failure instead of crashing if this Android image
    has no handler for any of those intents.

    CDXC:AndroidConnectionRecovery 2026-05-17-17:24:
    Some Android images can reject store/browser launch intents with a security
    or runtime launch error rather than ActivityNotFoundException. Treat those
    as recoverable so Ghostex can keep the user in the setup/recovery panel
    with visible status copy instead of crashing during connection repair.
    */
    public static boolean launch(@NonNull Activity activity) {
        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
        if (launchIntent != null && tryStart(activity, launchIntent)) return true;
        if (tryStart(activity, marketIntent())) return true;
        return tryStart(activity, webIntent());
    }

    static Intent marketIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URI));
    }

    static Intent webIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URI));
    }

    private static boolean tryStart(@NonNull Activity activity, @NonNull Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        } catch (SecurityException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

}
