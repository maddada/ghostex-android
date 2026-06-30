package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
public final class GhostexSessionInventoryClientTest {

    /*
    CDXC:AndroidConnectionRecovery 2026-05-17-12:32:
    Failed reconnect recovery depends on SSH stderr being summarized correctly.
    These mappings drive the drawer state card and password prompt path, so test
    common failure strings directly instead of relying on a live remote Mac.

    CDXC:AndroidRemoteSessions 2026-05-17-12:38:
    Session inventory should stay machine-readable JSON while tolerating SSH
    login banners and shell text around the JSON payload.

    CDXC:AndroidRemoteSessions 2026-05-17-13:42:
    Login banners and shell profiles can print brace-delimited text before
    `ghostex sessions --json`; Android should select the first JSON object that
    actually contains the sessions array.

    CDXC:AndroidConnectionRecovery 2026-05-17-13:00:
    Common SSH and remote CLI failures should become actionable Ghostex
    recovery copy instead of raw terminal stderr.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:25:
    Android remote action failures can now arrive as nonzero CLI exits with a
    JSON `{ ok: false, error }` payload. The drawer should show the error text,
    not raw JSON, so recovery action sheets remain readable.

    CDXC:AndroidRemoteSessions 2026-05-17-14:32:
    Real login shell text can include unmatched brace snippets before Ghostex
    CLI output. Inventory and failed-action parsing should skip those snippets
    and keep scanning for a complete JSON object with the expected contract.

    CDXC:AndroidConnectionManagement 2026-05-17-18:20:
    Check connection now depends on `ghostex android-check --json`, so its JSON
    failures should map to update, zmx setting, and missing-zmx recovery copy.
    */

    @Test
    public void sessionsCommandRequestsMobileSummary() {
        /*
        CDXC:AndroidRemoteSessionsPerformance 2026-06-30-19:16:
        Android fetches large Mac session lists over SSH and should request the
        compact mobile summary payload instead of the full desktop diagnostic
        JSON contract.
        */
        Assert.assertEquals("ghostex sessions --json --mobile-summary",
            GhostexSessionInventoryClient.SESSIONS_COMMAND);
    }

    @Test
    public void parseSessionsExtractsJsonFromSshFramingAndFiltersToZmx() throws Exception {
        String output = "Last login: Sun May 17\n" +
            "{\n" +
            "  \"ok\": true,\n" +
            "  \"sessions\": [\n" +
            "    {\"alias\": 1, \"sessionId\": \"session-zmx\", \"title\": \"Ship Android\", \"sessionPersistenceProvider\": \"zmx\", \"sessionPersistenceName\": \"zmx-main\", \"shouldSubmitStagedFirstPromptTitleCommand\": true},\n" +
            "    {\"alias\": 2, \"sessionId\": \"session-tmux\", \"title\": \"Skip\", \"sessionPersistenceProvider\": \"tmux\"}\n" +
            "  ]\n" +
            "}\n" +
            "logout\n";

        List<GhostexRemoteSession> sessions = GhostexSessionInventoryClient.parseSessions(output);

        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals("session-zmx", sessions.get(0).sessionId);
        Assert.assertEquals("Ship Android", sessions.get(0).title);
        Assert.assertEquals("zmx-main", sessions.get(0).providerSessionName);
        Assert.assertTrue(sessions.get(0).shouldSubmitStagedFirstPromptTitleCommand);
    }

    @Test
    public void extractFirstJsonObjectIgnoresBracesInsideStringsAndTrailingText() throws Exception {
        String extracted = GhostexSessionInventoryClient.extractFirstJsonObject(
            "banner\n{\"sessions\":[{\"alias\":1,\"sessionId\":\"s1\",\"title\":\"literal } brace\",\"sessionPersistenceProvider\":\"zmx\"}]}\nprofile done");

        Assert.assertTrue(extracted.startsWith("{\"sessions\""));
        Assert.assertTrue(extracted.endsWith("}]}"));
    }

    @Test
    public void parseSessionsSkipsEarlierJsonWithoutSessionsArray() throws Exception {
        String output = "profile {\"motd\":\"Welcome to Mac\"}\n" +
            "{\"ok\":true,\"sessions\":[{\"alias\":1,\"sessionId\":\"s1\",\"title\":\"Ghostex\",\"sessionPersistenceProvider\":\"zmx\"}]}\n";

        List<GhostexRemoteSession> sessions = GhostexSessionInventoryClient.parseSessions(output);

        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals("s1", sessions.get(0).sessionId);
    }

    @Test
    public void parseSessionsSkipsBalancedBraceBannerBeforeCliJson() throws Exception {
        String output = "Last login {not-json banner}\n" +
            "{\"sessions\":[{\"alias\":2,\"sessionId\":\"s2\",\"title\":\"Work\",\"sessionPersistenceProvider\":\"zmx\"}]}";

        List<GhostexRemoteSession> sessions = GhostexSessionInventoryClient.parseSessions(output);

        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals("s2", sessions.get(0).sessionId);
    }

    @Test
    public void parseSessionsSkipsUnbalancedBraceBannerBeforeCliJson() throws Exception {
        String output = "profile function prompt { unfinished banner\n" +
            "{\"sessions\":[{\"alias\":3,\"sessionId\":\"s3\",\"title\":\"Work\",\"sessionPersistenceProvider\":\"zmx\"}]}";

        List<GhostexRemoteSession> sessions = GhostexSessionInventoryClient.parseSessions(output);

        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals("s3", sessions.get(0).sessionId);
    }

    @Test
    public void parseSessionsRequiresSessionsArrayPayload() {
        try {
            GhostexSessionInventoryClient.parseSessions("{\"motd\":\"Welcome\"}");
            Assert.fail("Expected missing sessions payload to fail.");
        } catch (Exception error) {
            Assert.assertEquals("Ghostex CLI did not return a sessions JSON payload.", error.getMessage());
        }
    }

    @Test
    public void parseCreatedSessionIdReadsCompactCreateSessionPayload() throws Exception {
        String output = "Last login\n" +
            "{\"ok\":true,\"revision\":12,\"session\":{\"sessionId\":\"session-new\",\"title\":\"Ghostex\"}}\n";

        Assert.assertEquals("session-new", GhostexSessionInventoryClient.parseCreatedSessionId(output));
    }

    @Test
    public void parseCreatedSessionIdIgnoresFailedCreatePayload() throws Exception {
        String output = "{\"ok\":false,\"error\":\"Could not create session.\"}";

        Assert.assertNull(GhostexSessionInventoryClient.parseCreatedSessionId(output));
    }

    @Test
    public void summarizesPermissionDeniedForSavedPassword() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "Permission denied, please try again.", true);

        Assert.assertEquals("SSH rejected the saved password. Update the saved machine password or choose another machine.", message);
    }

    @Test
    public void summarizesPermissionDeniedForMissingPassword() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "Permission denied (publickey,password).", false);

        Assert.assertEquals("SSH needs a key or password. Open the machine settings and save a password, or configure SSH keys/Tailscale SSH.", message);
    }

    @Test
    public void summarizesTailscaleReachabilityFailures() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "ssh: connect to host mac.tailnet.ts.net port 22: No route to host", false);

        Assert.assertEquals("Could not reach the machine. Open Tailscale and confirm both devices are online.", message);
    }

    @Test
    public void summarizesHostKeyVerificationFailure() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "Host key verification failed.", false);

        Assert.assertEquals("SSH host key verification failed. Open Setup and reset this phone's saved host key for the machine, or confirm you are connecting to the right Mac.", message);
    }

    @Test
    public void summarizesConnectionRefused() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "ssh: connect to host mac.tailnet.ts.net port 22: Connection refused", false);

        Assert.assertEquals("The machine is reachable, but SSH refused the connection. Enable Remote Login on the Mac and confirm the saved SSH port.", message);
    }

    @Test
    public void summarizesMissingRemoteGhostexCli() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "zsh:1: command not found: ghostex", false);

        Assert.assertEquals("Connected over SSH, but the Mac could not find the Ghostex CLI. Install Ghostex CLI and make sure `ghostex` is available in the SSH login shell.", message);
    }

    @Test
    public void summarizesMissingRemoteZmx() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "session persistence is set to zmx, but zmx was not found on PATH.", false);

        Assert.assertEquals("Ghostex is reachable, but ZMX is not available on the Mac. Install zmx and set Ghostex session persistence to zmx.", message);
    }

    @Test
    public void summarizesMissingAndroidCheckCommand() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "Unknown command: android-check", false);

        Assert.assertEquals("Connected over SSH, but this Mac has an older Ghostex CLI. Update Ghostex so `ghostex android-check --json` is available.", message);
    }

    @Test
    public void summarizesNonZmxPersistenceFromAndroidCheckJson() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "{\"ok\":false,\"error\":\"Ghostex session persistence is set to tmux. Open Ghostex Settings and set Session persistence to zmx before connecting from Android.\"}", false);

        Assert.assertEquals("Ghostex is reachable, but Session persistence is not set to zmx. Open Ghostex Settings on the Mac and set Session persistence to zmx.", message);
    }

    @Test
    public void summarizesJsonCliBridgeFailureError() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "Last login\n{\"ok\":false,\"error\":\"Session was already closed on the Mac.\"}\nlogout", false);

        Assert.assertEquals("Session was already closed on the Mac.", message);
    }

    @Test
    public void mapsJsonCliBridgeFailureErrorToRecoveryCopy() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "{\"ok\":false,\"error\":\"Permission denied (publickey,password).\"}", false);

        Assert.assertEquals("SSH needs a key or password. Open the machine settings and save a password, or configure SSH keys/Tailscale SSH.", message);
    }

    @Test
    public void scansPastEarlierJsonWhenSummarizingCliFailure() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "profile {\"motd\":\"Welcome\"}\n{\"bridgeOk\":false,\"message\":\"Could not focus this session.\"}", false);

        Assert.assertEquals("Could not focus this session.", message);
    }

    @Test
    public void scansPastUnbalancedBraceWhenSummarizingCliFailure() {
        String message = GhostexSessionInventoryClient.summarizeFailure(
            "profile function prompt { unfinished\n{\"ok\":false,\"error\":\"Could not wake this session.\"}", false);

        Assert.assertEquals("Could not wake this session.", message);
    }

    @Test
    public void trimsLongCliFailuresForDrawerDisplay() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 260; i++) builder.append('x');

        String message = GhostexSessionInventoryClient.summarizeFailure(builder.toString(), false);

        Assert.assertEquals(223, message.length());
        Assert.assertTrue(message.endsWith("..."));
    }

}
