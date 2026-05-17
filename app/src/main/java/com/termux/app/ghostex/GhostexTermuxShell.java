package com.termux.app.ghostex;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class GhostexTermuxShell {

    private final Context context;
    private final TermuxShellEnvironment shellEnvironment = new TermuxShellEnvironment();

    /*
    CDXC:AndroidRemoteSessions 2026-05-17-18:24:
    Non-interactive Ghostex reconnect and setup checks must run with the same
    Termux HOME, PREFIX, TMPDIR, PATH, and library environment as visible
    terminal sessions. A plain Android shell with only PATH patched can miss
    Termux state such as known_hosts and make release reconnects flaky.
    */
    public GhostexTermuxShell(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public ProcessBuilder newShellProcess(@NonNull String command) {
        ExecutionCommand executionCommand = new ExecutionCommand(0,
            "/system/bin/sh",
            new String[] { "-lc", command },
            null,
            TermuxConstants.TERMUX_HOME_DIR_PATH,
            ExecutionCommand.Runner.APP_SHELL.getName(),
            false);
        executionCommand.setShellCommandShellEnvironment = true;

        HashMap<String, String> termuxEnvironment =
            shellEnvironment.setupShellCommandEnvironment(context, executionCommand);
        ensureSshDirectory();

        ProcessBuilder processBuilder = new ProcessBuilder("/system/bin/sh", "-lc", command);
        Map<String, String> environment = processBuilder.environment();
        environment.clear();
        environment.putAll(termuxEnvironment);
        processBuilder.directory(new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
        return processBuilder.redirectErrorStream(true);
    }

    private void ensureSshDirectory() {
        File sshDirectory = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".ssh");
        if (!sshDirectory.exists()) sshDirectory.mkdirs();
        sshDirectory.setReadable(true, true);
        sshDirectory.setWritable(true, true);
        sshDirectory.setExecutable(true, true);
    }

}
