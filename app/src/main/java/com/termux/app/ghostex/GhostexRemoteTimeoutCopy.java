package com.termux.app.ghostex;

import androidx.annotation.NonNull;

public final class GhostexRemoteTimeoutCopy {

    private GhostexRemoteTimeoutCopy() {}

    /*
    CDXC:AndroidConnectionRecovery 2026-05-17-19:07:
    SSH timeouts are common when phones move between networks or Tailscale is
    waking a route. Keep timeout copy centralized so reconnect, readiness
    checks, lifecycle actions, and rename all describe the interrupted intent
    and point users back to Tailscale plus retry.
    */
    public static String connecting(@NonNull GhostexMachine machine) {
        return "Timed out while connecting to " + machine.displayLabel() + ". Open Tailscale and confirm both devices are online, then retry.";
    }

    public static String checking(@NonNull GhostexMachine machine) {
        return "Timed out while checking " + machine.displayLabel() + ". Open Tailscale and confirm both devices are online, then retry.";
    }

    public static String sessionAction(@NonNull String action) {
        return "Timed out while running ghostex " + action + ". Open Tailscale and retry this session action.";
    }

    public static String uploadingImage() {
        /*
        CDXC:AndroidImageAttach 2026-05-18-01:39:
        Image attach uses scp rather than the Ghostex CLI, so timeout recovery
        copy must describe file upload instead of a `ghostex` session action.
        */
        return "Timed out while uploading the image. Open Tailscale and retry image attach.";
    }

    public static String renaming() {
        return "Timed out while renaming this session. Open Tailscale and retry the rename.";
    }
}
