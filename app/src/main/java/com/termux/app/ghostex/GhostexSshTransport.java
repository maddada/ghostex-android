package com.termux.app.ghostex;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.schmizz.sshj.AndroidConfig;
import net.schmizz.sshj.Config;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.kex.KeyExchange;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class GhostexSshTransport {

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int COMMAND_TIMEOUT_MS = 18_000;
    private static final int UPLOAD_TIMEOUT_MS = 60_000;
    private static final String HOST_KEY_PREFS = "ghostex_ssh_host_keys";

    private final Context context;

    /*
    CDXC:AndroidSshTransport 2026-05-18-02:56:
    Ghostex Android needs a Play-compliant SSH path that does not execute
    Termux package binaries from app-private storage. Keep SSH exec and SFTP
    upload in an app-owned transport so listing, actions, readiness, and file
    transfer can run at modern target SDKs.

    CDXC:AndroidConnectionSecurity 2026-05-18-03:02:
    Match OpenSSH's `StrictHostKeyChecking=accept-new` behavior at the app
    layer: trust a host key the first time a saved machine connects, persist
    its SHA-256 fingerprint by host and port, and reject changed keys.

    CDXC:AndroidSshTransport 2026-05-18-03:31:
    SSHJ's default key-exchange order prefers Curve25519/X25519, but Android's
    active BouncyCastle provider can report `no such algorithm: X25519 for
    provider BC`. Use AndroidConfig and remove curve25519 KEX factories so
    Ghostex negotiates macOS OpenSSH-compatible ECDH/DH algorithms instead of
    surfacing a raw provider error in the drawer.

    CDXC:AndroidSshTransport 2026-05-18-04:04:
    Android ships an old platform provider named `BC` that can also lack `EC`.
    Replace that process-local provider with the bundled BouncyCastle provider
    before SSHJ builds key-exchange primitives, otherwise ECDH negotiation fails
    with `no such algorithm: EC for provider BC`.
    */
    public GhostexSshTransport(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public CommandResult exec(@NonNull GhostexMachine machine,
                              @Nullable String password,
                              @NonNull String remoteCommand) {
        return exec(machine, password, remoteCommand, null);
    }

    @NonNull
    public CommandResult exec(@NonNull GhostexMachine machine,
                              @Nullable String password,
                              @NonNull String remoteCommand,
                              @Nullable String sessionLogTag) {
        if (password == null || password.isEmpty()) {
            return CommandResult.failure("SSH needs a saved password until app-owned key auth is added.");
        }
        SSHClient ssh = null;
        try {
            GhostexFileLogger.log(context, "ssh", sessionLogTag,
                "exec start machine=" + machine.displayLabel() + " command=" + safeCommandLabel(remoteCommand));
            ssh = openAuthenticatedClient(machine, password, sessionLogTag);
            try (Session session = ssh.startSession()) {
                Session.Command command = session.exec(remoteCommand);
                command.join(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                Integer exitStatus = command.getExitStatus();
                if (exitStatus == null) {
                    command.close();
                    String output = readStream(command.getInputStream());
                    String error = readStream(command.getErrorStream());
                    GhostexFileLogger.log(context, "ssh", sessionLogTag,
                        "exec timed out machine=" + machine.displayLabel() + " outputBytes=" +
                            combineOutput(output, error).length());
                    return CommandResult.timeout(combineOutput(output, error));
                }
                String output = readStream(command.getInputStream());
                String error = readStream(command.getErrorStream());
                String combined = combineOutput(output, error);
                GhostexFileLogger.log(context, "ssh", sessionLogTag,
                    "exec finished machine=" + machine.displayLabel() + " exit=" + exitStatus +
                        " outputBytes=" + combined.length());
                return new CommandResult(exitStatus, combined, false);
            }
        } catch (Exception error) {
            GhostexFileLogger.log(context, "ssh", sessionLogTag,
                "exec failed machine=" + machine.displayLabel(), error);
            return CommandResult.failure(error.getMessage() == null ? "SSH command failed." : error.getMessage());
        } finally {
            if (ssh != null) {
                try {
                    ssh.disconnect();
                } catch (Exception ignored) {
                    // Closing a failed SSH connection can throw; the command result above is the useful error.
                }
            }
        }
    }

    @NonNull
    private static String safeCommandLabel(@NonNull String remoteCommand) {
        String compact = remoteCommand.replace('\n', ' ').replace('\r', ' ');
        return compact.length() <= 180 ? compact : compact.substring(0, 180) + "...";
    }

    @NonNull
    public CommandResult upload(@NonNull GhostexMachine machine,
                                @Nullable String password,
                                @NonNull File localFile,
                                @NonNull String remotePath) {
        if (password == null || password.isEmpty()) {
            return CommandResult.failure("SSH needs a saved password until app-owned key auth is added.");
        }
        SSHClient ssh = null;
        try {
            ssh = openAuthenticatedClient(machine, password);
            try (SFTPClient sftp = ssh.newSFTPClient()) {
                mkdirs(sftp, remoteDirectory(remotePath));
                sftp.put(new FileSystemFile(localFile), remotePath);
                return new CommandResult(0, "", false);
            }
        } catch (Exception error) {
            return CommandResult.failure(error.getMessage() == null ? "File upload failed." : error.getMessage());
        } finally {
            if (ssh != null) {
                try {
                    ssh.disconnect();
                } catch (Exception ignored) {
                    // Closing a failed SSH connection can throw; the upload result above is the useful error.
                }
            }
        }
    }

    @NonNull
    public CommandResult resetPersistedHostKey(@NonNull GhostexMachine machine) {
        /*
        CDXC:AndroidConnectionRecovery 2026-05-18-06:47:
        Ghostex Android no longer installs phone-side SSH packages, so host-key
        repair must clear SSHJ's app-owned accepted-host-key store instead of
        editing Termux-era host-key files.
        */
        String storageKey = hostKeyStorageKey(machine);
        SharedPreferences prefs = context.getSharedPreferences(HOST_KEY_PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(storageKey)) {
            return new CommandResult(0, "No saved SSH host key was stored for " + machine.connectionTarget() + ".", false);
        }
        prefs.edit().remove(storageKey).apply();
        return new CommandResult(0, "Saved SSH host key reset for " + machine.connectionTarget() + ". Retrying connection...", false);
    }

    @NonNull
    SSHClient openAuthenticatedClient(@NonNull GhostexMachine machine,
                                      @Nullable String password) throws Exception {
        return openAuthenticatedClient(machine, password, null);
    }

    @NonNull
    SSHClient openAuthenticatedClient(@NonNull GhostexMachine machine,
                                      @Nullable String password,
                                      @Nullable String sessionLogTag) throws Exception {
        if (password == null || password.isEmpty()) {
            GhostexFileLogger.log(context, "ssh", sessionLogTag, "missing password machine=" + machine.displayLabel() +
                " target=" + machine.connectionTarget());
            throw new IllegalArgumentException("SSH needs a saved password until app-owned key auth is added.");
        }
        SSHClient ssh = newSshClient(machine);
        boolean connected = false;
        try {
            GhostexFileLogger.log(context, "ssh", sessionLogTag, "connecting machine=" + machine.displayLabel() +
                " target=" + machine.connectionTarget());
            ssh.connect(machine.host, machine.port);
            connected = true;
            GhostexFileLogger.log(context, "ssh", sessionLogTag, "connected machine=" + machine.displayLabel());
            ssh.authPassword(machine.username, password);
            GhostexFileLogger.log(context, "ssh", sessionLogTag, "authenticated machine=" + machine.displayLabel());
            return ssh;
        } catch (Exception error) {
            GhostexFileLogger.log(context, "ssh", sessionLogTag, "connect/auth failed machine=" + machine.displayLabel() +
                " connected=" + connected, error);
            try {
                if (connected) ssh.disconnect();
                else ssh.close();
            } catch (Exception ignored) {
                // The original connection/authentication error is the actionable failure.
            }
            throw error;
        }
    }

    private SSHClient newSshClient(@NonNull GhostexMachine machine) {
        ensureBundledBouncyCastleProvider();
        SSHClient ssh = new SSHClient(createSshConfig());
        ssh.setConnectTimeout(CONNECT_TIMEOUT_MS);
        ssh.setTimeout(UPLOAD_TIMEOUT_MS);
        ssh.addHostKeyVerifier(new PersistedHostKeyVerifier(machine));
        return ssh;
    }

    static void ensureBundledBouncyCastleProvider() {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider instanceof BouncyCastleProvider) return;
        if (provider != null) Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    static Config createSshConfig() {
        AndroidConfig config = new AndroidConfig();
        ArrayList<Factory.Named<KeyExchange>> supportedFactories = new ArrayList<>();
        for (Factory.Named<KeyExchange> factory : config.getKeyExchangeFactories()) {
            if (!isUnsupportedAndroidKeyExchange(factory.getName())) supportedFactories.add(factory);
        }
        config.setKeyExchangeFactories(supportedFactories);
        return config;
    }

    private static boolean isUnsupportedAndroidKeyExchange(@Nullable String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("curve25519");
    }

    private void mkdirs(@NonNull SFTPClient sftp, @NonNull String remoteDirectory) throws Exception {
        if (remoteDirectory.isEmpty() || ".".equals(remoteDirectory) || "/".equals(remoteDirectory)) return;
        StringBuilder current = new StringBuilder(remoteDirectory.startsWith("/") ? "/" : "");
        String[] parts = remoteDirectory.split("/");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current.length() > 1) current.append('/');
            current.append(part);
            try {
                sftp.mkdir(current.toString());
            } catch (Exception ignored) {
                // Existing remote directories are expected on repeated file uploads.
            }
        }
    }

    @NonNull
    private static String readStream(@Nullable InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }

    @NonNull
    private static String combineOutput(@NonNull String output, @NonNull String error) {
        if (output.isEmpty()) return error;
        if (error.isEmpty()) return output;
        return output + "\n" + error;
    }

    @NonNull
    private static String remoteDirectory(@NonNull String remotePath) {
        int slashIndex = remotePath.lastIndexOf('/');
        if (slashIndex <= 0) return ".";
        return remotePath.substring(0, slashIndex);
    }

    public static final class CommandResult {
        public final int exitCode;
        public final String output;
        public final boolean timedOut;

        private CommandResult(int exitCode, @NonNull String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        static CommandResult failure(@NonNull String output) {
            return new CommandResult(1, output, false);
        }

        static CommandResult timeout(@NonNull String output) {
            return new CommandResult(-1, output, true);
        }
    }

    private final class PersistedHostKeyVerifier implements HostKeyVerifier {
        private final GhostexMachine machine;

        PersistedHostKeyVerifier(@NonNull GhostexMachine machine) {
            this.machine = machine;
        }

        @Override
        public boolean verify(String hostname, int port, PublicKey key) {
            String fingerprint = fingerprint(key);
            String storageKey = hostKeyStorageKey(machine);
            SharedPreferences prefs = context.getSharedPreferences(HOST_KEY_PREFS, Context.MODE_PRIVATE);
            String savedFingerprint = prefs.getString(storageKey, "");
            if (savedFingerprint == null || savedFingerprint.isEmpty()) {
                prefs.edit().putString(storageKey, fingerprint).apply();
                return true;
            }
            return savedFingerprint.equals(fingerprint);
        }

        @Override
        public List<String> findExistingAlgorithms(String hostname, int port) {
            return Collections.emptyList();
        }
    }

    @NonNull
    private static String hostKeyStorageKey(@NonNull GhostexMachine machine) {
        return machine.host + ":" + machine.port;
    }

    @NonNull
    private static String fingerprint(@NonNull PublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception error) {
            return key.getAlgorithm() + ":" + key.hashCode();
        }
    }

}
