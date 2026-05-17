package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class GhostexAccessibilityCopy {

    private GhostexAccessibilityCopy() {}

    /*
    CDXC:AndroidSidebar 2026-05-17-16:12:
    Ghostex drawer and action-sheet rows are custom touch targets. Compose their
    accessibility copy from visible labels, supporting details, and available
    actions so tap and long-press workflows remain understandable without visual
    sidebar scanning.
    */
    @NonNull
    public static String join(@Nullable String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (builder.length() > 0) {
                char previous = builder.charAt(builder.length() - 1);
                builder.append(previous == '.' || previous == '!' || previous == '?' ? " " : ". ");
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

}
