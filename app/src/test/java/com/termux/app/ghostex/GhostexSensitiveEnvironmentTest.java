package com.termux.app.ghostex;

import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class GhostexSensitiveEnvironmentTest {

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-18:31:
    Saved SSH passwords use SSHPASS only as process environment, so execution
    logging must prove it redacts secret-like variables before writing verbose
    Termux environment dumps.
    */
    @Test
    public void redactsSshpassAndKeepsNonSecretEnvironmentReadable() {
        List<String> redacted = ShellEnvironmentUtils.redactSensitiveEnvironmentForLogging(Arrays.asList(
            "PATH=/data/data/com.termux/files/usr/bin",
            "SSHPASS=correct horse battery staple",
            "GHOSTEX_TOKEN=secret-token"
        ));

        Assert.assertTrue(redacted.contains("PATH=/data/data/com.termux/files/usr/bin"));
        Assert.assertTrue(redacted.contains("SSHPASS=<redacted>"));
        Assert.assertTrue(redacted.contains("GHOSTEX_TOKEN=<redacted>"));
        Assert.assertFalse(redacted.contains("SSHPASS=correct horse battery staple"));
        Assert.assertFalse(redacted.contains("GHOSTEX_TOKEN=secret-token"));
    }

}
