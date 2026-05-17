package com.termux.app.ghostex;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public final class GhostexPasswordVaultTest {

    private static final String PREFS_NAME = "ghostex_android_passwords";

    private Context context;
    private SharedPreferences preferences;
    private GhostexPasswordVault vault;

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-15:29:
    Saved SSH passwords are encrypted separately from machine metadata, so the
    vault needs an explicit orphan cleanup path. Machine self-healing and
    deletion should not leave encrypted credentials for removed machine ids.
    */
    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.deleteSharedPreferences(PREFS_NAME);
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        vault = new GhostexPasswordVault(context);
    }

    @Test
    public void prunePasswordsRemovesOrphanMachineEntries() {
        preferences.edit()
            .putString("ssh_password_mac-a", "encrypted-a")
            .putString("ssh_password_deleted", "encrypted-deleted")
            .putString("ssh_password_bad/id", "encrypted-bad")
            .putString("other_setting", "keep")
            .apply();

        vault.prunePasswordsForMachineIds(Arrays.asList("mac-a", "bad/id"));

        Assert.assertTrue(preferences.contains("ssh_password_mac-a"));
        Assert.assertFalse(preferences.contains("ssh_password_deleted"));
        Assert.assertFalse(preferences.contains("ssh_password_bad/id"));
        Assert.assertTrue(preferences.contains("other_setting"));
    }

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-17:36:
    Password vault operations should validate machine ids at the credential
    boundary, not only trust the saved-machine store that usually calls them.

    CDXC:AndroidConnectionSecurity 2026-05-17-19:59:
    Stale or malformed saved-password envelopes should self-heal out of the
    vault instead of keeping Settings and reconnect in a false saved-password
    state.
    */
    @Test
    public void passwordVaultRejectsInvalidMachineIds() throws Exception {
        assertInvalidMachineIdFails(() -> vault.hasPassword("bad/id"));
        assertInvalidMachineIdFails(() -> vault.deletePassword("bad id"));
        assertInvalidMachineIdFails(() -> vault.readPassword("bad\nid"));
        assertInvalidMachineIdFails(() -> vault.savePassword("bad:id", "secret"));
    }

    @Test
    public void hasPasswordRemovesMalformedSavedPasswordEnvelope() {
        preferences.edit()
            .putString("ssh_password_mac-a", "not-a-vault-envelope")
            .apply();

        Assert.assertFalse(vault.hasPassword("mac-a"));
        Assert.assertFalse(preferences.contains("ssh_password_mac-a"));
    }

    @Test
    public void readPasswordRemovesMalformedSavedPasswordEnvelope() throws Exception {
        preferences.edit()
            .putString("ssh_password_mac-a", "bad-base64:also-bad")
            .apply();

        try {
            vault.readPassword("mac-a");
            Assert.fail("Expected malformed password envelope to fail.");
        } catch (IllegalArgumentException expected) {
            Assert.assertFalse(preferences.contains("ssh_password_mac-a"));
        }
    }

    private void assertInvalidMachineIdFails(ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
            Assert.fail("Expected invalid machine id to fail.");
        } catch (IllegalArgumentException error) {
            Assert.assertTrue(error.getMessage().startsWith("Invalid Ghostex SSH machine id for password vault:"));
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

}
