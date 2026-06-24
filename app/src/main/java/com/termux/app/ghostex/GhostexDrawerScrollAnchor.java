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
        if (item.type == GhostexDrawerItem.Type.PROJECT_HEADER) {
            return "project:" + item.projectKey;
        }
        if (item.type == GhostexDrawerItem.Type.PROJECT_SESSION_LIST_TOGGLE) {
            return "project-session-toggle:" + item.projectKey;
        }
        if (item.type == GhostexDrawerItem.Type.SESSION && item.session != null) {
            return "session:" + item.session.sessionId;
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
