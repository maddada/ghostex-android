package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

public final class GhostexSessionCardFormatterTest {

    /*
    CDXC:AndroidSidebar 2026-05-17-12:45:
    Last Active and activity priority are part of the Ghostex sidebar contract.
    Test them separately from Android views so card metadata and reconnect
    ordering keep matching the macOS sidebar as the drawer evolves.

    CDXC:AndroidSidebar 2026-05-17-12:59:
    The long-press Details sheet should reuse the drawer's Last Active wording
    while providing an explicit Unknown value when the CLI has no timestamp.

    CDXC:AndroidSidebar 2026-05-17-14:44:
    Android should keep Last Active labels and sorting if the Mac bridge emits
    ISO timestamps with explicit local offsets instead of UTC `Z` strings.
    */

    @Test
    public void buildMetaIncludesProjectLastActiveProviderAndAgent() {
        long nowMs = 1_779_008_400_000L;
        GhostexRemoteSession session = session("1", "idle", "2026-05-17T08:58:00Z");

        String meta = GhostexSessionCardFormatter.buildMeta(session, nowMs);

        Assert.assertEquals("Ghostex · 2m ago · zmx-main · codex", meta);
    }

    @Test
    public void formatLastActiveHandlesSecondsHoursAndDays() {
        long nowMs = 1_779_008_400_000L;

        Assert.assertEquals("30s ago", GhostexSessionCardFormatter.formatLastActive("2026-05-17T08:59:30Z", nowMs));
        Assert.assertEquals("2h ago", GhostexSessionCardFormatter.formatLastActive("2026-05-17T07:00:00.000Z", nowMs));
        Assert.assertEquals("2d ago", GhostexSessionCardFormatter.formatLastActive("2026-05-15T08:00:00Z", nowMs));
    }

    @Test
    public void formatLastActiveHandlesIsoOffsetTimestamps() {
        long nowMs = 1_779_008_400_000L;

        Assert.assertEquals("2m ago", GhostexSessionCardFormatter.formatLastActive("2026-05-17T12:58:00+04:00", nowMs));
        Assert.assertEquals("2m ago", GhostexSessionCardFormatter.formatLastActive("2026-05-17T12:58:00.123456+04:00", nowMs));
        Assert.assertEquals("2m ago", GhostexSessionCardFormatter.formatLastActive("2026-05-17T12:58:00+0400", nowMs));
    }

    @Test
    public void formatLastActiveDetailUsesUnknownForMissingTimestamps() {
        long nowMs = 1_779_008_400_000L;

        Assert.assertEquals("Unknown", GhostexSessionCardFormatter.formatLastActiveDetail("", nowMs));
        Assert.assertEquals("2m ago", GhostexSessionCardFormatter.formatLastActiveDetail("2026-05-17T08:58:00Z", nowMs));
    }

    @Test
    public void sidebarOrderPrioritizesAttentionWorkingThenRecency() {
        ArrayList<GhostexRemoteSession> sessions = new ArrayList<>();
        sessions.add(session("idle-new", "idle", "2026-05-17T08:59:00Z"));
        sessions.add(session("working-old", "working", "2026-05-17T08:00:00Z"));
        sessions.add(session("attention-old", "attention", "2026-05-17T07:00:00Z"));
        sessions.add(session("idle-old", "idle", "2026-05-17T12:00:00+04:00"));

        Collections.sort(sessions, GhostexSessionCardFormatter::compareForSidebarOrder);

        Assert.assertEquals("session-attention-old", sessions.get(0).sessionId);
        Assert.assertEquals("session-working-old", sessions.get(1).sessionId);
        Assert.assertEquals("session-idle-new", sessions.get(2).sessionId);
        Assert.assertEquals("session-idle-old", sessions.get(3).sessionId);
    }

    private GhostexRemoteSession session(String alias, String activity, String lastInteractionAt) {
        return new GhostexRemoteSession(alias, "session-" + alias, "project-1", "Session " + alias,
            "Ghostex", "/Users/madda/dev/_active/zmux", activity, activity, "zmx",
            "zmx-main", "codex", lastInteractionAt, false, false);
    }

}
