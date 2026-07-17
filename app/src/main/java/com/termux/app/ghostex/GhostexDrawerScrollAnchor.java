package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

final class GhostexDrawerScrollAnchor {

    final String anchorKey;
    final int topOffset;

    GhostexDrawerScrollAnchor(@NonNull String anchorKey, int topOffset) {
        this.anchorKey = anchorKey;
        this.topOffset = topOffset;
    }

    /*
    CDXC:AndroidSidebar 2026-05-19-10:45:
    Inventory refreshes restore scroll by stable project/session keys so row
    inserts above the viewport do not jump the drawer back to the top.
    */
    @Nullable
    static String keyForItem(@NonNull GhostexDrawerItem item) {
        /*
        CDXC:AndroidSidebar 2026-07-18:
        Anchor keys are machine-scoped now that every saved machine's section
        renders in one stacked drawer. Two Macs can expose the same project key
        (for example "chats") or session id, and an unscoped anchor could
        restore the viewport into the wrong machine's section.
        */
        String machinePrefix = item.machineId().isEmpty() ? "" : item.machineId() + "::";
        if (item.type == GhostexDrawerItem.Type.MACHINE_HEADER) {
            return "machine:" + item.machineId();
        }
        if (item.type == GhostexDrawerItem.Type.PROJECT_HEADER) {
            return machinePrefix + "project:" + item.projectKey;
        }
        if (item.type == GhostexDrawerItem.Type.PROJECT_SESSION_LIST_TOGGLE) {
            return machinePrefix + "project-session-toggle:" + item.projectKey;
        }
        if (item.type == GhostexDrawerItem.Type.GROUP_HEADER) {
            return machinePrefix + "group:" + item.groupCollapseKey;
        }
        if (item.type == GhostexDrawerItem.Type.PROJECT_AGENTS_ROW) {
            return machinePrefix + "project-agents:" + item.projectKey;
        }
        if (item.type == GhostexDrawerItem.Type.PROJECT_EMPTY) {
            return machinePrefix + "project-empty:" + item.projectKey;
        }
        if (item.type == GhostexDrawerItem.Type.SESSION && item.session != null) {
            return machinePrefix + "session:" + item.session.sessionId;
        }
        return null;
    }

    /*
    CDXC:AndroidSidebar 2026-06-23-08:27:
    Reopening the Android sidebar must preserve the user's last session-list viewport even if notification polling rebuilt the hidden ListView.
    Capture the first visible row with a stable sidebar key, not only child zero, because long project lists can put a Show more/less control or transient row at the top of the viewport.
    */
    @Nullable
    static GhostexDrawerScrollAnchor firstStableVisibleAnchor(@NonNull List<GhostexDrawerItem> items,
                                                              int firstVisiblePosition,
                                                              int visibleChildCount,
                                                              @NonNull VisibleChildTopProvider topProvider) {
        if (firstVisiblePosition < 0 || visibleChildCount <= 0) return null;
        for (int childIndex = 0; childIndex < visibleChildCount; childIndex++) {
            int itemIndex = firstVisiblePosition + childIndex;
            if (itemIndex < 0 || itemIndex >= items.size()) continue;
            String anchorKey = keyForItem(items.get(itemIndex));
            if (anchorKey == null) continue;
            return new GhostexDrawerScrollAnchor(anchorKey, topProvider.topForChild(childIndex));
        }
        return null;
    }

    interface VisibleChildTopProvider {
        int topForChild(int childIndex);
    }
}
