package com.termux.app.ghostex;

import androidx.annotation.NonNull;

public final class GhostexOnboardingGuide {

    private GhostexOnboardingGuide() {}

    /*
	    CDXC:AndroidOnboarding 2026-05-17-16:58:
	    The first-run tutorial is part of the release product contract, not loose
	    dialog copy. Keep the title, connection model, and exact setup steps in a
	    small helper so Android and later iOS work can verify the same onboarding
	    requirements: Tailscale on both devices, macOS Remote Login, Ghostex CLI,
	    zmx persistence, built-in SSHJ transport, saved machine setup, reconnect,
	    and warm session switching.

	    CDXC:AndroidRemoteSessions 2026-06-11-23:52:
	    Onboarding must not teach users that the macOS app window is the status
	    service. Android asks the Mac CLI for gxserver-backed session inventory, so
	    a running GX server is enough for statuses after sessions exist.
	    */
    public static final String TITLE = "Connect to your running Ghostex sessions";

	    public static final String INTRO =
	        "Ghostex Android reaches your Mac over Tailscale SSH, asks the Ghostex CLI for the GX server-backed session list, and attaches this Termux terminal to the selected ZMX-backed session.";

    private static final String[] STEPS = new String[] {
        "On the Mac, install and sign in to Tailscale.",
        "In Tailscale, confirm the Mac is online and note its Tailscale host name or IP address.",
        "Enable SSH on the Mac from System Settings > General > Sharing > Remote Login.",
        "Install Ghostex and make sure the `ghostex` CLI is available in the login shell used by SSH.",
        "In Terminal on the Mac, run: command -v ghostex && command -v zmx. Both commands should print a path before you add the machine here.",
        "In Ghostex Settings on the Mac, set Session persistence to zmx. Android only supports zmx for now.",
	        "Start Ghostex or GX server on the Mac and leave the sessions you want to continue running. The macOS app does not need to stay open for Android to refresh statuses.",
        "On this phone, install Tailscale, sign in to the same tailnet, and confirm the Mac is reachable.",
        "In Ghostex Android, use the built-in Setup panel for Tailscale, machine management, tutorial review, and host-key repair. You do not need to install SSH packages on the phone.",
        "Add the Mac in Ghostex Android with a display name, host, username, and SSH port.",
        "Tap Retry or reopen the app. Ghostex Android will reconnect to the last selected machine, show ZMX sessions, and keep your last seven opened sessions warm for fast switching."
    };

    @NonNull
    public static String[] steps() {
        return STEPS.clone();
    }

}
