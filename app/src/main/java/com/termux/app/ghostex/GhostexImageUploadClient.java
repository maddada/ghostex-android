package com.termux.app.ghostex;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class GhostexImageUploadClient {

    private final GhostexTermuxShell termuxShell;

    /*
    CDXC:AndroidImageAttach 2026-05-18-01:39:
    Selected Android images must be uploaded to the selected Mac over SSH, then
    referenced in the terminal as markdown so the remote agent can read the
    Mac-side file path. Keep this as a dedicated SSH client because image
    upload is file transfer, not a Ghostex CLI action.
    */
    public GhostexImageUploadClient(@NonNull Context context) {
        termuxShell = new GhostexTermuxShell(context);
    }

    public Result uploadImage(@NonNull GhostexMachine machine,
                              @Nullable String password,
                              @NonNull File localFile,
                              @NonNull String remotePath) {
        boolean hasPassword = password != null && !password.isEmpty();
        String command = GhostexSshCommandBuilder.buildImageUploadCommand(machine, hasPassword,
            localFile.getAbsolutePath(), remotePath);
        try {
            CommandResult commandResult = runShellCommand(command, password);
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.uploadingImage());
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(GhostexSessionInventoryClient.summarizeFailure(
                    commandResult.output, hasPassword));
            }
            return Result.success(remotePath);
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not upload image." : error.getMessage());
        }
    }

    private CommandResult runShellCommand(@NonNull String command, @Nullable String password) throws Exception {
        boolean hasPassword = password != null && !password.isEmpty();
        ProcessBuilder processBuilder = termuxShell.newShellProcess(command);
        processBuilder.redirectErrorStream(true);
        if (hasPassword) processBuilder.environment().put("SSHPASS", password);
        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        Thread readerThread = new Thread(() -> readAll(process.getInputStream(), output), "ghostex-image-upload-output");
        readerThread.start();
        boolean finished = waitForProcess(process, 60_000L);
        if (!finished) {
            process.destroy();
            readerThread.join(500L);
            return new CommandResult(-1, output.toString(), true);
        }
        readerThread.join(1_000L);
        return new CommandResult(process.exitValue(), output.toString(), false);
    }

    private void readAll(@NonNull InputStream inputStream, @NonNull StringBuilder builder) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(line);
            }
        } catch (Exception ignored) {
            // The process may be destroyed while the reader is blocked.
        }
    }

    private boolean waitForProcess(@NonNull Process process, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException ignored) {
                Thread.sleep(100L);
            }
        }
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException ignored) {
            return false;
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String errorMessage;
        public final String remotePath;

        private Result(boolean ok, @Nullable String errorMessage, @Nullable String remotePath) {
            this.ok = ok;
            this.errorMessage = errorMessage;
            this.remotePath = remotePath;
        }

        public static Result success(@NonNull String remotePath) {
            return new Result(true, null, remotePath);
        }

        public static Result failure(@NonNull String errorMessage) {
            return new Result(false, errorMessage, null);
        }
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        final boolean timedOut;

        CommandResult(int exitCode, @NonNull String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }
    }

}
