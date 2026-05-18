package com.termux.app.ghostex;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public final class GhostexFileUploadClient {

    private final GhostexSshTransport sshTransport;

    /*
    CDXC:AndroidFileAttach 2026-05-18-04:56:
    Selected Android files must be uploaded to the selected Mac over SSH, then
    referenced in the terminal as markdown so the remote agent can read the
    Mac-side file path. Keep this as a dedicated SSH client because attachment
    upload is file transfer, not a Ghostex CLI action.
    */
    public GhostexFileUploadClient(@NonNull Context context) {
        sshTransport = new GhostexSshTransport(context);
    }

    public Result uploadFile(@NonNull GhostexMachine machine,
                             @Nullable String password,
                             @NonNull File localFile,
                             @NonNull String remotePath) {
        boolean hasPassword = password != null && !password.isEmpty();
        try {
            GhostexSshTransport.CommandResult commandResult = sshTransport.upload(machine, password, localFile, remotePath);
            if (commandResult.timedOut) {
                return Result.failure(GhostexRemoteTimeoutCopy.uploadingFile());
            }
            if (commandResult.exitCode != 0) {
                return Result.failure(GhostexSessionInventoryClient.summarizeFailure(
                    commandResult.output, hasPassword));
            }
            return Result.success(remotePath);
        } catch (Exception error) {
            return Result.failure(error.getMessage() == null ? "Could not upload file." : error.getMessage());
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

}
