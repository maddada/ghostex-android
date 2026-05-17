package com.termux.app.ghostex;

import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class GhostexEdgeToEdgeInsets {

    private GhostexEdgeToEdgeInsets() {}

    /*
    CDXC:AndroidReleaseSurface 2026-05-17-20:24:
    Android 15 forces edge-to-edge for target API 35 apps. Ghostex Android keeps
    the Termux activity structure intact, but the remote-session drawer and
    terminal surface must apply system-bar and display-cutout insets so the
    sidebar, terminal rows, and floating keyboard button are not hidden by the
    status bar, navigation bar, or cutout on release devices.
    */
    public static void applyToReleaseSurface(@NonNull View releaseSurface) {
        if (Build.VERSION.SDK_INT < 35) return;

        final int initialLeft = releaseSurface.getPaddingLeft();
        final int initialTop = releaseSurface.getPaddingTop();
        final int initialRight = releaseSurface.getPaddingRight();
        final int initialBottom = releaseSurface.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(releaseSurface, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                initialLeft + insets.left,
                initialTop + insets.top,
                initialRight + insets.right,
                initialBottom + insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(releaseSurface);
    }

}
