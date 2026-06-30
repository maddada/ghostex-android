package com.termux.app.ghostex;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalSession;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GhostexSshAttachProcess implements TerminalSession.ExternalTerminalProcess {

    private final Context context;
    private final GhostexMachine machine;
    private final String password;
    private final String remoteCommand;
    private final String sessionLogTag;
    private final ExecutorService resizeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "GhostexSshAttachResize");
        thread.setDaemon(true);
        return thread;
    });
    private final Object resizeLock = new Object();

    @Nullable private SSHClient ssh;
    @Nullable private Session session;
    @Nullable private Session.Shell shell;
    @Nullable private ResizeRequest pendingResize;
    private boolean stdoutEofLogged;
    private boolean resizeWorkerRunning;
    private boolean closed;
    private long stdinBytes;
    private long stdoutBytes;
    private long stdoutEvents;
    private long stdinEvents;
    private long resizeEvents;
    private long slowQueueEvents;
    private long nextStdoutLogBytes = 256 * 1024L;
    private long lastStatsLogAt;

    /*
    CDXC:AndroidRemoteAttach 2026-05-18-04:53:
    Tapping a remote session should attach to the Mac-side persistent ZMX
    terminal through app-owned SSHJ, not by spawning Termux package binaries.
    This preserves Play-compliant target SDK behavior while keeping
    the terminal UI, warm-session cache, and PTY interaction model intact.

    CDXC:AndroidRemoteAttachLatency 2026-06-30-19:16:
    Android attach startup may be direct `zmx attach` for live provider rows or
    fallback `ghostex attach` for rows that need CLI resolution. Both paths need
    to behave like a real interactive SSH terminal, so start an SSH shell
    channel after PTY allocation and `exec` the login-shell command inside it
    instead of using SSH exec directly.

    CDXC:AndroidRemoteAttach 2026-05-18-06:25:
    The direct PTY geometry and SSHJ auto-expand experiment did not change the
    resize crash, so keep attach behavior close to a normal interactive SSH shape and
    add counters around resize, read, write, and terminal-queue pressure. The
    goal is to identify whether the remote EOF follows resize, input, large
    output bursts, or Android queue backpressure.

    CDXC:AndroidRemoteAttach 2026-05-18-06:34:
    SSHJ sends `window-change` by writing to the network socket. Android zoom
    invokes terminal resize from the UI/touch/layout path, so dispatch remote
    PTY resizes onto a single background worker and coalesce rapid pinch events.
    This prevents `NetworkOnMainThreadException` without reverting to OpenSSH.
    */
    public GhostexSshAttachProcess(@NonNull Context context,
                                   @NonNull GhostexMachine machine,
                                   @Nullable String password,
                                   @NonNull String remoteCommand,
                                   @NonNull String sessionLogTag) {
        this.context = context.getApplicationContext();
        this.machine = machine;
        this.password = password;
        this.remoteCommand = remoteCommand;
        this.sessionLogTag = sessionLogTag;
    }

    @Override
    public void start(int columns, int rows, int cellWidthPixels, int cellHeightPixels) throws Exception {
        GhostexFileLogger.log(context, "attach", sessionLogTag, "start machineId=" + machine.id +
            " size=" + columns + "x" + rows + " commandBytes=" + remoteCommand.length());
        SSHClient nextSsh = new GhostexSshTransport(context).openAuthenticatedClient(machine, password, sessionLogTag);
        GhostexFileLogger.log(context, "attach", sessionLogTag, "authenticated machineId=" + machine.id);
        Session nextSession = nextSsh.startSession();
        try {
            nextSession.allocatePTY("xterm-256color", columns, rows, cellWidthPixels, cellHeightPixels,
                Collections.<PTYMode, Integer>emptyMap());
            GhostexFileLogger.log(context, "attach", sessionLogTag, "pty allocated size=" + columns + "x" + rows +
                " cell=" + cellWidthPixels + "x" + cellHeightPixels);
            Session.Shell nextShell = nextSession.startShell();
            String shellCommand = "exec " + GhostexSshCommandBuilder.loginShellCommand(remoteCommand) + "\n";
            nextShell.getOutputStream().write(shellCommand.getBytes(StandardCharsets.UTF_8));
            nextShell.getOutputStream().flush();
            GhostexFileLogger.log(context, "attach", sessionLogTag, "remote shell command started autoExpand=" +
                nextShell.getAutoExpand() + " localWindow=" + nextShell.getLocalWinSize() +
                " remoteWindow=" + nextShell.getRemoteWinSize());
            ssh = nextSsh;
            session = nextSession;
            shell = nextShell;
        } catch (Exception error) {
            GhostexFileLogger.log(context, "attach", sessionLogTag, "start failed after authentication", error);
            try {
                nextSession.close();
            } catch (Exception ignored) {
                // The exec/PTY error above is the useful failure.
            }
            try {
                nextSsh.disconnect();
            } catch (Exception ignored) {
                // The exec/PTY error above is the useful failure.
            }
            throw error;
        }
    }

    @Override
    public InputStream getInputStream() {
        if (shell == null) throw new IllegalStateException("Ghostex SSH attach has not started.");
        return new LoggingInputStream(shell.getInputStream());
    }

    @Override
    public OutputStream getOutputStream() {
        if (shell == null) throw new IllegalStateException("Ghostex SSH attach has not started.");
        return new LoggingOutputStream(shell.getOutputStream());
    }

    @Override
    public void resize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) throws Exception {
        if (shell == null) return;
        ResizeRequest request;
        synchronized (resizeLock) {
            resizeEvents++;
            request = new ResizeRequest(resizeEvents, columns, rows, cellWidthPixels, cellHeightPixels);
            pendingResize = request;
            if (closed) return;
            if (resizeWorkerRunning) {
                GhostexFileLogger.log(context, "attach", sessionLogTag, "pty resize coalesced size=" +
                    columns + "x" + rows + " cell=" + cellWidthPixels + "x" + cellHeightPixels +
                    " " + statsSnapshot());
                return;
            }
            resizeWorkerRunning = true;
        }
        GhostexFileLogger.log(context, "attach", sessionLogTag, "pty resize queued size=" + columns + "x" + rows +
            " cell=" + cellWidthPixels + "x" + cellHeightPixels + " " + statsSnapshot());
        resizeExecutor.execute(this::drainResizeRequests);
    }

    @Override
    public int waitFor() throws Exception {
        if (shell == null) return 1;
        GhostexFileLogger.log(context, "attach", sessionLogTag, "waiting for remote shell command");
        try {
            shell.join();
            String stderr = readAvailableErrorStream(shell.getErrorStream());
            GhostexFileLogger.log(context, "attach", sessionLogTag, "remote shell command closed " + statsSnapshot() +
                (stderr.isEmpty() ? "" : " stderr=" + stderr));
            return 0;
        } catch (Exception error) {
            GhostexFileLogger.log(context, "attach", sessionLogTag, "wait failed " + statsSnapshot(), error);
            throw error;
        }
    }

    @Override
    public void close() throws Exception {
        GhostexFileLogger.log(context, "attach", sessionLogTag, "close requested");
        synchronized (resizeLock) {
            closed = true;
            pendingResize = null;
        }
        resizeExecutor.shutdownNow();
        Exception firstError = null;
        try {
            if (shell != null) shell.close();
        } catch (Exception error) {
            GhostexFileLogger.log(context, "attach", sessionLogTag, "shell close failed", error);
            firstError = error;
        }
        try {
            if (session != null) session.close();
        } catch (Exception error) {
            GhostexFileLogger.log(context, "attach", sessionLogTag, "session close failed", error);
            if (firstError == null) firstError = error;
        }
        try {
            if (ssh != null) ssh.disconnect();
        } catch (Exception error) {
            GhostexFileLogger.log(context, "attach", sessionLogTag, "ssh disconnect failed", error);
            if (firstError == null) firstError = error;
        }
        GhostexFileLogger.log(context, "attach", sessionLogTag, "close finished");
        if (firstError != null) throw firstError;
    }

    @Override
    public void onProcessOutput(int bytesRead, long queueWriteMs, boolean queued) {
        stdoutBytes += Math.max(0, bytesRead);
        stdoutEvents++;
        if (queueWriteMs > 100) slowQueueEvents++;
        long now = System.currentTimeMillis();
        if (!queued || queueWriteMs > 250 || stdoutBytes >= nextStdoutLogBytes || now - lastStatsLogAt > 5000) {
            while (stdoutBytes >= nextStdoutLogBytes) nextStdoutLogBytes += 256 * 1024L;
            lastStatsLogAt = now;
            GhostexFileLogger.log(context, "attach-io", sessionLogTag,
                "stdout chunk bytes=" + bytesRead + " queued=" + queued +
                    " queueMs=" + queueWriteMs + " " + statsSnapshot());
        }
    }

    @Override
    public void onTerminalInput(int bytesToWrite) {
        stdinBytes += Math.max(0, bytesToWrite);
        stdinEvents++;
        GhostexFileLogger.log(context, "attach-io", sessionLogTag,
            "stdin chunk bytes=" + bytesToWrite + " " + statsSnapshot());
    }

    @Override
    public void onExternalProcessExit(int exitCode) {
        GhostexFileLogger.log(context, "attach", sessionLogTag,
            "terminal waiter posting exitCode=" + exitCode + " " + statsSnapshot());
    }

    @Override
    public void onExternalProcessWaitFailed(@NonNull Exception error) {
        GhostexFileLogger.log(context, "attach", sessionLogTag,
            "terminal waiter caught failure " + statsSnapshot(), error);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    @NonNull
    private static String readAvailableErrorStream(@Nullable InputStream errorStream) {
        try {
            int available = errorStream == null ? 0 : errorStream.available();
            if (available <= 0) return "";
            byte[] buffer = new byte[Math.min(available, 8192)];
            int read = errorStream.read(buffer);
            if (read <= 0) return "";
            return safe(new String(buffer, 0, read, "UTF-8"));
        } catch (Exception ignored) {
            return "";
        }
    }

    private void drainResizeRequests() {
        while (true) {
            ResizeRequest request;
            synchronized (resizeLock) {
                request = pendingResize;
                pendingResize = null;
                if (request == null || closed) {
                    resizeWorkerRunning = false;
                    return;
                }
            }
            Session.Shell activeShell = shell;
            if (activeShell == null) continue;
            try {
                activeShell.changeWindowDimensions(request.columns, request.rows,
                    request.cellWidthPixels, request.cellHeightPixels);
                GhostexFileLogger.log(context, "attach", sessionLogTag, "pty resize sent seq=" + request.sequence +
                    " size=" + request.columns + "x" + request.rows + " cell=" +
                    request.cellWidthPixels + "x" + request.cellHeightPixels + " " + statsSnapshot());
            } catch (Exception error) {
                GhostexFileLogger.log(context, "attach", sessionLogTag, "pty resize failed seq=" + request.sequence +
                    " size=" + request.columns + "x" + request.rows + " cell=" +
                    request.cellWidthPixels + "x" + request.cellHeightPixels + " " + statsSnapshot(), error);
            }
        }
    }

    @NonNull
    private String statsSnapshot() {
        String window = "";
        try {
            if (shell != null) {
                window = " localWindow=" + shell.getLocalWinSize() + " remoteWindow=" + shell.getRemoteWinSize() +
                    " eof=" + shell.isEOF() + " open=" + shell.isOpen();
            }
        } catch (Exception ignored) {
            window = "";
        }
        return "stdoutBytes=" + stdoutBytes + " stdoutEvents=" + stdoutEvents +
            " stdinBytes=" + stdinBytes + " stdinEvents=" + stdinEvents +
            " resizes=" + resizeEvents + " slowQueueEvents=" + slowQueueEvents + window;
    }

    private final class LoggingInputStream extends FilterInputStream {
        LoggingInputStream(@NonNull InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public int read() throws IOException {
            try {
                int value = super.read();
                logEof(value == -1);
                return value;
            } catch (IOException error) {
                GhostexFileLogger.log(context, "attach", sessionLogTag, "stdout read failed " + statsSnapshot(), error);
                throw error;
            }
        }

        @Override
        public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
            try {
                int count = super.read(buffer, byteOffset, byteCount);
                logEof(count == -1);
                return count;
            } catch (IOException error) {
                GhostexFileLogger.log(context, "attach", sessionLogTag, "stdout read failed " + statsSnapshot(), error);
                throw error;
            }
        }

        private void logEof(boolean eof) {
            if (!eof || stdoutEofLogged) return;
            stdoutEofLogged = true;
            GhostexFileLogger.log(context, "attach", sessionLogTag, "stdout EOF");
        }
    }

    private final class LoggingOutputStream extends FilterOutputStream {
        LoggingOutputStream(@NonNull OutputStream outputStream) {
            super(outputStream);
        }

        @Override
        public void write(int value) throws IOException {
            try {
                super.write(value);
            } catch (IOException error) {
                GhostexFileLogger.log(context, "attach", sessionLogTag, "stdin write failed " + statsSnapshot(), error);
                throw error;
            }
        }

        @Override
        public void write(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
            try {
                out.write(buffer, byteOffset, byteCount);
            } catch (IOException error) {
                GhostexFileLogger.log(context, "attach", sessionLogTag, "stdin write failed " + statsSnapshot(), error);
                throw error;
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                super.flush();
            } catch (IOException error) {
                GhostexFileLogger.log(context, "attach", sessionLogTag, "stdin flush failed " + statsSnapshot(), error);
                throw error;
            }
        }
    }

    private static final class ResizeRequest {
        final long sequence;
        final int columns;
        final int rows;
        final int cellWidthPixels;
        final int cellHeightPixels;

        ResizeRequest(long sequence, int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
            this.sequence = sequence;
            this.columns = columns;
            this.rows = rows;
            this.cellWidthPixels = cellWidthPixels;
            this.cellHeightPixels = cellHeightPixels;
        }
    }
}
