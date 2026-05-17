package com.termux.app.ghostex;

import android.app.AlertDialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class GhostexDialogStyler {

    private static final int GHOSTEX_BG = GhostexPalette.BACKGROUND;
    private static final int GHOSTEX_ACCENT = GhostexPalette.BUTTON;
    private static final int GHOSTEX_MUTED = GhostexPalette.MUTED;
    private static final int GHOSTEX_DANGER = GhostexPalette.DANGER;

    private GhostexDialogStyler() {}

    /*
    CDXC:AndroidSidebar 2026-05-17-17:40:
    Ghostex Android presents machine settings, onboarding, context menus, and
    confirmations as product surfaces, not stock Termux dialogs. Centralize
    AlertDialog styling so every drawer-driven panel keeps the same dark window,
    readable action colors, and destructive-action emphasis while Termux
    upstream dialog code stays untouched.
    */
    public static void show(@NonNull AlertDialog dialog) {
        show(dialog, false, null);
    }

    public static void show(@NonNull AlertDialog dialog, @Nullable Runnable afterShow) {
        show(dialog, false, afterShow);
    }

    public static void showDestructive(@NonNull AlertDialog dialog, @Nullable Runnable afterShow) {
        show(dialog, true, afterShow);
    }

    private static void show(@NonNull AlertDialog dialog, boolean destructivePositive,
                             @Nullable Runnable afterShow) {
        dialog.setOnShowListener(ignored -> {
            styleWindow(dialog);
            styleButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                destructivePositive ? GHOSTEX_DANGER : GHOSTEX_ACCENT);
            styleButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), GHOSTEX_MUTED);
            styleButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), GHOSTEX_MUTED);
            if (afterShow != null) afterShow.run();
        });
        dialog.show();
    }

    private static void styleWindow(@NonNull AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(GHOSTEX_BG));
        window.setDimAmount(0.68f);
    }

    private static void styleButton(@Nullable Button button, int color) {
        if (button == null) return;
        button.setTextColor(color);
        button.setAllCaps(false);
    }

}
