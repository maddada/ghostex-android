package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
        if (item.type == GhostexDrawerItem.Type.SESSION && item.session != null) {
            return "session:" + item.session.sessionId;
        }
        return null;
    }
}
