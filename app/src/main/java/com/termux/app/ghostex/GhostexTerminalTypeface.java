package com.termux.app.ghostex;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.termux.R;

import java.io.File;

public final class GhostexTerminalTypeface {

    private GhostexTerminalTypeface() {}

    /*
    CDXC:AndroidTerminalFont 2026-05-26-10:14:
    Ghostex Android should render terminal text with bundled JetBrains Mono Nerd
    Font by default so Nerd Font glyphs are available on phones without requiring
    a user-managed `~/.termux/font.ttf`. Keep the user font file as an explicit
    override, but make the app-owned font the normal release path.
    */
    @NonNull
    public static Typeface resolve(@NonNull Context context, @Nullable File userFontFile) {
        if (userFontFile != null && userFontFile.exists() && userFontFile.length() > 0) {
            return Typeface.createFromFile(userFontFile);
        }
        Typeface bundledTypeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_nerd_font_regular);
        if (bundledTypeface == null) {
            throw new IllegalStateException("Bundled JetBrains Mono Nerd Font resource is unavailable.");
        }
        return bundledTypeface;
    }

}
