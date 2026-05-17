package com.termux.app.ghostex;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class GhostexRemoteActionFeedback {

    private GhostexRemoteActionFeedback() {}

    /*
    CDXC:AndroidSidebar 2026-05-17-14:27:
    Remote context-menu actions should finish with explicit confirmation copy,
    not a generic reconnect status. Keep these strings testable so focus, wake,
    sleep, kill, and project actions feel intentional after the drawer refreshes.
    */
    @NonNull
    public static String singleSuccess(@NonNull String action, @NonNull GhostexRemoteSession session) {
        String target = session.alias.isEmpty() ? "this session" : "session " + session.alias;
        switch (normalize(action)) {
            case "focus":
                return "Focused " + target + " on the Mac.";
            case "wake":
                return "Woke " + target + ".";
            case "sleep":
                return "Slept " + target + ".";
            case "kill":
                return "Killed " + target + ".";
            default:
                return "Updated " + target + ".";
        }
    }

    @NonNull
    public static String projectSuccess(@NonNull String action, @NonNull String projectTitle, int count) {
        String target = count == 1 ? "1 session" : count + " sessions";
        String project = projectTitle.trim().isEmpty() ? "this project" : projectTitle.trim();
        switch (normalize(action)) {
            case "wake":
                return "Woke " + target + " in " + project + ".";
            case "sleep":
                return "Slept " + target + " in " + project + ".";
            case "kill":
                return "Killed " + target + " in " + project + ".";
            default:
                return "Updated " + target + " in " + project + ".";
        }
    }

    @NonNull
    public static String renameSuccess(@NonNull GhostexRemoteSession session) {
        String target = session.alias.isEmpty() ? "session" : "session " + session.alias;
        return "Renamed " + target + ".";
    }

    private static String normalize(@NonNull String action) {
        return action.trim().toLowerCase(Locale.US);
    }

}
