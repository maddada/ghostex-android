package com.termux.app.ghostex;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

public final class GhostexTextSemantics {

    private GhostexTextSemantics() {}

    /*
    CDXC:AndroidSidebar 2026-05-17-17:47:
    Ghostex Android moved dialog titles into custom dark content panels. Mark
    those title TextViews as accessibility headings so screen-reader navigation
    preserves the structure that platform AlertDialog title chrome used to
    provide, while keeping the visual surface fully Ghostex-styled.
    */
    public static void markHeading(@NonNull TextView view) {
        ViewCompat.setAccessibilityHeading(view, true);
    }

}
