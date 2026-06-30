package com.termux.app.ghostex;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexFileLoggerTest {

    /*
    CDXC:AndroidLoggingPrivacy 2026-06-30-03:27:
    Users may share `ghostex-android.log` directly from their phone. Test the writer-boundary sanitizer so future SSH diagnostics cannot persist raw machine names, SSH targets, commands, URLs, paths, output text, or secret-like key/value fields.
    */
    @Test
    public void persistentLogSanitizerRedactsPrivateSshFields() {
        String sanitized = GhostexFileLogger.sanitizeForPersistentLog(
            "machine=Work Mac target=madda@100.64.1.2 command=/bin/zsh -lc 'ghostex sessions --json' " +
                "url=https://example.com/setup?token=abc path=/Users/madda/project stdout=hello token=secret");

        Assert.assertFalse(sanitized.contains("Work Mac"));
        Assert.assertFalse(sanitized.contains("madda@100.64.1.2"));
        Assert.assertFalse(sanitized.contains("ghostex sessions"));
        Assert.assertFalse(sanitized.contains("example.com"));
        Assert.assertFalse(sanitized.contains("/Users/madda/project"));
        Assert.assertFalse(sanitized.contains("hello"));
        Assert.assertFalse(sanitized.contains("secret"));
        Assert.assertTrue(sanitized.contains("machine=<redacted>"));
        Assert.assertTrue(sanitized.contains("command=<redacted>"));
    }

    @Test
    public void persistentLogSanitizerKeepsStableIdsAndCounts() {
        String sanitized = GhostexFileLogger.sanitizeForPersistentLog(
            "machineId=550e8400-e29b-41d4-a716-446655440000 sessionId=session-123 exit=1 outputBytes=42 connected=false");

        Assert.assertTrue(sanitized.contains("machineId=550e8400-e29b-41d4-a716-446655440000"));
        Assert.assertTrue(sanitized.contains("sessionId=session-123"));
        Assert.assertTrue(sanitized.contains("exit=1"));
        Assert.assertTrue(sanitized.contains("outputBytes=42"));
        Assert.assertTrue(sanitized.contains("connected=false"));
    }
}
