package com.termux.app.ghostex;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Collection;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class GhostexPasswordVault {

    private static final String PREFS_NAME = "ghostex_android_passwords";
    private static final String KEY_ALIAS = "ghostex_android_saved_ssh_passwords";
    private static final String MACHINE_KEY_PREFIX = "ssh_password_";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences preferences;

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-10:13:
    SSH passwords may be saved only through Android Keystore-backed encryption.
    API 21-22 intentionally cannot save passwords because adding an insecure
    plaintext fallback would make reconnect easier by weakening the credential
    model.

    CDXC:AndroidConnectionSecurity 2026-05-17-10:37:
    Saved-machine validation needs to know whether an existing secure password
    is already present so edits do not force users to retype credentials while
    new saved-password machines still require a password before reconnect can
    be automated.

    CDXC:AndroidConnectionSecurity 2026-05-17-15:29:
    The password vault is intentionally separate from saved-machine metadata,
    but release builds must not retain encrypted credentials for machines that
    no longer exist. Prune orphan password entries whenever the controller has
    the current cleaned machine list.

    CDXC:AndroidConnectionSecurity 2026-05-17-17:36:
    Treat the vault as its own credential boundary. Validate machine ids before
    deriving SharedPreferences keys so future call paths cannot create, read,
    delete, or preserve arbitrary `ssh_password_*` entries with corrupt ids even
    if they bypass the saved-machine store.

    CDXC:AndroidConnectionSecurity 2026-05-17-19:59:
    Saved-password state must not be inferred from any stale blob in
    SharedPreferences. Remove malformed or undecryptable password envelopes as
    soon as the vault sees them so Settings and reconnect can fall back to
    explicit credential recovery instead of presenting a false saved-password
    state.
    */
    public GhostexPasswordVault(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isPasswordSavingSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public void savePassword(@NonNull String machineId, @NonNull String password) throws Exception {
        requireValidMachineId(machineId);
        if (!isPasswordSavingSupported())
            throw new IllegalStateException("Secure password saving requires Android 6.0 or newer.");
        savePasswordApi23(machineId, password);
    }

    @Nullable
    public String readPassword(@NonNull String machineId) throws Exception {
        requireValidMachineId(machineId);
        if (!isPasswordSavingSupported()) return null;
        String key = keyForMachine(machineId);
        String encoded = preferences.getString(key, null);
        if (encoded == null || encoded.trim().isEmpty()) return null;
        try {
            return readPasswordApi23(encoded);
        } catch (Exception error) {
            preferences.edit().remove(key).apply();
            throw error;
        }
    }

    public void deletePassword(@NonNull String machineId) {
        requireValidMachineId(machineId);
        preferences.edit().remove(keyForMachine(machineId)).apply();
    }

    public boolean hasPassword(@NonNull String machineId) {
        requireValidMachineId(machineId);
        if (!isPasswordSavingSupported()) return false;
        String key = keyForMachine(machineId);
        String encoded = preferences.getString(key, null);
        if (encoded == null || encoded.trim().isEmpty()) return false;
        if (!isStoredPasswordEnvelopeWellFormed(encoded)) {
            preferences.edit().remove(key).apply();
            return false;
        }
        return true;
    }

    public void prunePasswordsForMachineIds(@NonNull Collection<String> machineIds) {
        SharedPreferences.Editor editor = null;
        for (String key : preferences.getAll().keySet()) {
            if (!key.startsWith(MACHINE_KEY_PREFIX)) continue;
            String machineId = key.substring(MACHINE_KEY_PREFIX.length());
            if (GhostexMachineValidation.machineIdError(machineId) == null && machineIds.contains(machineId)) continue;
            if (editor == null) editor = preferences.edit();
            editor.remove(key);
        }
        if (editor != null) editor.apply();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void savePasswordApi23(@NonNull String machineId, @NonNull String password) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        String value = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP);
        preferences.edit().putString(keyForMachine(machineId), value).apply();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private String readPasswordApi23(@NonNull String encoded) throws Exception {
        String[] parts = encoded.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Saved SSH password envelope is malformed.");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build());
        return keyGenerator.generateKey();
    }

    private boolean isStoredPasswordEnvelopeWellFormed(@NonNull String encoded) {
        String[] parts = encoded.split(":", 2);
        if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) return false;
        try {
            return Base64.decode(parts[0], Base64.NO_WRAP).length > 0 &&
                Base64.decode(parts[1], Base64.NO_WRAP).length > 0;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String keyForMachine(@NonNull String machineId) {
        return MACHINE_KEY_PREFIX + machineId;
    }

    private void requireValidMachineId(@NonNull String machineId) {
        String error = GhostexMachineValidation.machineIdError(machineId);
        if (error != null) {
            throw new IllegalArgumentException("Invalid Ghostex SSH machine id for password vault: " + error);
        }
    }

}
