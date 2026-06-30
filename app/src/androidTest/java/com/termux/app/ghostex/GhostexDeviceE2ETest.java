package com.termux.app.ghostex;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class GhostexDeviceE2ETest {

    /*
    CDXC:AndroidReleaseE2E 2026-05-17-16:30:
    A release candidate is not proven until a real Android runtime can reach a
    real Tailscale Mac over SSH, verify `ghostex` and `zmx`, parse live
    ZMX-backed sessions, prove attach command construction, and run at least
    one stable-session-id CLI action.
    These tests are intentionally argument-driven so release QA can point the
    same APK/test APK pair at whichever Mac is online without hardcoding
    credentials or tailnet host names in the repository.

    CDXC:AndroidReleaseE2E 2026-05-17-18:39:
    Optional SSH passwords are read from the debug app's private E2E file, not
    instrumentation arguments, so the release QA command line does not expose
    SSH secrets while still proving saved-password SSHJ paths.

    CDXC:AndroidReleaseE2E 2026-05-17-18:52:
    Delete the private E2E password file immediately after the first
    instrumentation read, including read-failure paths, and keep the password
    only in this test process. Host cleanup still runs, but the on-device file
    should not wait for test teardown.

    CDXC:AndroidReleaseE2E 2026-05-17-22:24:
    Password staging is part of the release proof for saved-password SSH paths.
    Read the private file to EOF instead of assuming one FileInputStream read is
    complete, then delete it immediately so QA can use long generated secrets
    without truncation or lingering device-side files.
    */

    @Test
    public void remoteMachinePassesGhostexZmxReadinessCheck() {
        E2EConfig config = E2EConfig.fromInstrumentationArguments();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GhostexSessionInventoryClient.Result result = new GhostexSessionInventoryClient(context)
            .checkConnection(config.machine, config.password);

        Assert.assertTrue(result.errorMessage, result.ok);
    }

    @Test
    public void remoteInventoryReturnsZmxSessionsAndStableActionsWork() {
        E2EConfig config = E2EConfig.fromInstrumentationArguments();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GhostexSessionInventoryClient client = new GhostexSessionInventoryClient(context);
        GhostexSessionInventoryClient.Result result = client.fetchSessions(config.machine, config.password);

        Assert.assertTrue(result.errorMessage, result.ok);
        if (!config.allowEmptySessions) {
            Assert.assertFalse("Ghostex CLI returned no ZMX-backed sessions for " +
                config.machine.connectionTarget() + ".", result.sessions.isEmpty());
        }
        if (result.sessions.isEmpty()) return;

        GhostexRemoteSession session = selectSession(result.sessions, config.sessionId);
        Assert.assertNotNull("Requested session id was not present in the remote Ghostex inventory.",
            session);

        GhostexSessionInventoryClient.Result focusResult = client.runSessionAction(config.machine,
            config.password, "focus", session);
        Assert.assertTrue(focusResult.errorMessage, focusResult.ok);
    }

    @Test
    public void remoteAttachCommandUsesFastProviderPathWhenAvailable() {
        E2EConfig config = E2EConfig.fromInstrumentationArguments();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        GhostexSessionInventoryClient.Result result = new GhostexSessionInventoryClient(context)
            .fetchSessions(config.machine, config.password);

        Assert.assertTrue(result.errorMessage, result.ok);
        Assume.assumeFalse("No remote ZMX sessions available to validate attach command.",
            result.sessions.isEmpty());

        GhostexRemoteSession session = selectSession(result.sessions, config.sessionId);
        Assert.assertNotNull("Requested session id was not present in the remote Ghostex inventory.",
            session);

        String command = GhostexSshCommandBuilder.buildCopyableAttachCommand(config.machine, session);
        /*
        CDXC:AndroidRemoteAttachLatency 2026-06-30-19:16:
        Device E2E should accept the fast direct zmx attach command for live
        provider rows and only require the stable Ghostex CLI selector for rows
        that do not expose live provider identity.
        */
        if (session.isZmxBacked() && !session.providerSessionName.isEmpty() &&
            ("exists".equals(session.providerSessionState) || session.isLive)) {
            Assert.assertTrue(command, command.contains("zmx attach"));
            Assert.assertTrue(command, command.contains(session.providerSessionName));
            Assert.assertFalse(command, command.contains("ghostex attach"));
        } else {
            Assert.assertTrue(command, command.contains("ghostex attach"));
            Assert.assertTrue(command, command.contains("--session-id"));
            Assert.assertTrue(command, command.contains(session.sessionId));
        }
    }

    private static GhostexRemoteSession selectSession(List<GhostexRemoteSession> sessions,
                                                     String requestedSessionId) {
        if (requestedSessionId == null || requestedSessionId.isEmpty()) return sessions.get(0);
        for (GhostexRemoteSession session : sessions) {
            if (requestedSessionId.equals(session.sessionId)) return session;
        }
        return null;
    }

    private static final class E2EConfig {
        private static boolean passwordLoaded;
        private static String cachedPassword;

        final GhostexMachine machine;
        final String password;
        final String sessionId;
        final boolean allowEmptySessions;

        private E2EConfig(GhostexMachine machine, String password, String sessionId,
                          boolean allowEmptySessions) {
            this.machine = machine;
            this.password = password;
            this.sessionId = sessionId;
            this.allowEmptySessions = allowEmptySessions;
        }

        static E2EConfig fromInstrumentationArguments() {
            Bundle args = InstrumentationRegistry.getArguments();
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            String host = value(args, "ghostexHost");
            String username = value(args, "ghostexUser");
            Assume.assumeTrue("Set ghostexHost and ghostexUser instrumentation arguments to run Ghostex Android E2E.",
                host != null && !host.isEmpty() && username != null && !username.isEmpty());

            int port = parsePort(value(args, "ghostexPort"));
            String password = readPasswordFile(context);
            GhostexMachine machine = GhostexMachine.create(firstNonEmpty(value(args, "ghostexName"),
                "E2E Mac"), host, username, port, password != null);
            return new E2EConfig(machine, password,
                firstNonEmpty(value(args, "ghostexSessionId"), ""),
                "1".equals(value(args, "ghostexAllowEmptySessions")));
        }

        private static int parsePort(String value) {
            if (value == null || value.isEmpty()) return 22;
            int port = GhostexMachineValidation.parsePort(value);
            if (port <= 0) throw new IllegalArgumentException("ghostexPort must be 1-65535.");
            return port;
        }

        private static String value(Bundle args, String key) {
            String value = args.getString(key);
            return value == null ? null : value.trim();
        }

        private static synchronized String readPasswordFile(Context context) {
            if (passwordLoaded) return cachedPassword;
            passwordLoaded = true;

            File file = new File(new File(context.getFilesDir(), "ghostex-e2e"), "password");
            if (!file.isFile()) return null;
            IOException cleanupError = null;
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                FileInputStream input = new FileInputStream(file);
                try {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                } finally {
                    input.close();
                }
                String password = new String(output.toByteArray(), StandardCharsets.UTF_8);
                cachedPassword = password.isEmpty() ? null : password;
                return cachedPassword;
            } catch (IOException error) {
                cleanupError = error;
                throw new IllegalStateException("Could not read Ghostex Android E2E password file.", error);
            } finally {
                if (!file.delete() && file.exists()) {
                    IOException deleteError = new IOException("Could not delete Ghostex Android E2E password file.");
                    if (cleanupError != null) cleanupError.addSuppressed(deleteError);
                    else throw new IllegalStateException(deleteError);
                }
            }
        }

        private static String firstNonEmpty(String first, String fallback) {
            return first == null || first.isEmpty() ? fallback : first;
        }
    }
}
