package com.termux.app.ghostex;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GhostexMachineStore {

    private static final String PREFS_NAME = "ghostex_android_machines";
    private static final String KEY_MACHINES = "machines_json";
    private static final String KEY_LAST_MACHINE_ID = "last_machine_id";
    private static final String KEY_TUTORIAL_SEEN = "tutorial_seen";

    private final SharedPreferences preferences;

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-10:13:
    Saved SSH machines are normal settings metadata, while passwords live in
    GhostexPasswordVault. This split lets the app reconnect to the last machine
    and populate machine dropdowns without putting secrets in ordinary
    SharedPreferences.

    CDXC:AndroidConnectionManagement 2026-05-17-13:53:
    Async readiness checks must verify the target machine still exists before
    opening setup or credential recovery. Deleted machines should not be
    resurrected by a slow Check connection callback.

    CDXC:AndroidConnectionManagement 2026-05-17-13:55:
    The persisted last-machine id should never point at a deleted or invalid
    machine. Scrub stale selections when writing or resolving the last machine
    so automatic reconnect starts from a real saved account.

    CDXC:AndroidConnectionSecurity 2026-05-17-14:57:
    Treat machine storage as a release boundary, not just a cache. Self-heal
    invalid persisted machine JSON and reject invalid programmatic saves so
    automatic reconnect never restores malformed SSH targets or credential keys.

    CDXC:AndroidConnectionManagement 2026-05-17-15:27:
    Any rewrite of the saved-machine list must also validate the persisted
    selected id. Self-healing can remove corrupt selected records, and the
    account switcher should not keep pointing at a machine that no longer
    exists until a later reconnect happens to scrub it.

    CDXC:AndroidConnectionManagement 2026-05-17-21:07:
    Saved-machine settings are a multi-account manager. Prevent duplicate SSH
    targets so the drawer dropdown cannot show two entries that reconnect to
    the same host/user/port while still allowing distinct usernames or ports on
    the same Tailscale machine. Self-heal older duplicate persisted entries
    during load so upgrades cannot keep ambiguous reconnect targets.
    */
    public GhostexMachineStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<GhostexMachine> getMachines() {
        ArrayList<GhostexMachine> machines = new ArrayList<>();
        String jsonText = preferences.getString(KEY_MACHINES, "[]");
        try {
            JSONArray array = new JSONArray(jsonText);
            boolean shouldRewrite = false;
            Set<String> seenTargets = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                GhostexMachine machine = GhostexMachine.fromJson(array.optJSONObject(i));
                if (machine != null && seenTargets.add(sshTargetKey(machine.host, machine.username, machine.port))) {
                    machines.add(machine);
                } else {
                    shouldRewrite = true;
                }
            }
            if (shouldRewrite) writeMachines(machines);
        } catch (JSONException ignored) {
            preferences.edit()
                .putString(KEY_MACHINES, "[]")
                .remove(KEY_LAST_MACHINE_ID)
                .apply();
            return machines;
        }
        return machines;
    }

    public void saveMachine(@NonNull GhostexMachine machine) {
        validatePersistableMachine(machine);
        List<GhostexMachine> machines = getMachines();
        String duplicateMachineId = duplicateSshTargetId(machines, machine.host, machine.username,
            machine.port, machine.id);
        if (duplicateMachineId != null) {
            throw new IllegalArgumentException("Duplicate Ghostex SSH target cannot be saved: " + machine.connectionTarget());
        }
        boolean replaced = false;
        for (int i = 0; i < machines.size(); i++) {
            if (machines.get(i).id.equals(machine.id)) {
                machines.set(i, machine);
                replaced = true;
                break;
            }
        }
        if (!replaced) machines.add(machine);
        writeMachines(machines);
    }

    public boolean hasMachine(@NonNull String machineId) {
        for (GhostexMachine machine : getMachines()) {
            if (machine.id.equals(machineId)) return true;
        }
        return false;
    }

    @Nullable
    public String findDuplicateSshTargetId(@NonNull String host, @NonNull String username,
                                           int port, @Nullable String exceptMachineId) {
        return duplicateSshTargetId(getMachines(), host, username, port, exceptMachineId);
    }

    public void deleteMachine(@NonNull String machineId) {
        List<GhostexMachine> machines = getMachines();
        ArrayList<GhostexMachine> next = new ArrayList<>();
        for (GhostexMachine machine : machines) {
            if (!machine.id.equals(machineId)) next.add(machine);
        }
        writeMachines(next);
        if (machineId.equals(getLastMachineId())) {
            preferences.edit().remove(KEY_LAST_MACHINE_ID).apply();
        }
    }

    @Nullable
    public String getLastMachineId() {
        String value = preferences.getString(KEY_LAST_MACHINE_ID, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public void setLastMachineId(@Nullable String machineId) {
        SharedPreferences.Editor editor = preferences.edit();
        if (machineId == null || machineId.trim().isEmpty() || !hasMachine(machineId)) {
            editor.remove(KEY_LAST_MACHINE_ID);
        } else {
            editor.putString(KEY_LAST_MACHINE_ID, machineId);
        }
        editor.apply();
    }

    @Nullable
    public GhostexMachine getLastMachine() {
        String id = getLastMachineId();
        List<GhostexMachine> machines = getMachines();
        if (id != null) {
            for (GhostexMachine machine : machines) {
                if (machine.id.equals(id)) return machine;
            }
            setLastMachineId(null);
        }
        return machines.isEmpty() ? null : machines.get(0);
    }

    public boolean hasSeenTutorial() {
        return preferences.getBoolean(KEY_TUTORIAL_SEEN, false);
    }

    public void setTutorialSeen(boolean value) {
        preferences.edit().putBoolean(KEY_TUTORIAL_SEEN, value).apply();
    }

    private void writeMachines(@NonNull List<GhostexMachine> machines) {
        JSONArray array = new JSONArray();
        for (GhostexMachine machine : machines) {
            try {
                array.put(machine.toJson());
            } catch (JSONException ignored) {
                // Skip invalid records instead of corrupting the whole saved machine list.
            }
        }
        SharedPreferences.Editor editor = preferences.edit().putString(KEY_MACHINES, array.toString());
        String lastMachineId = getLastMachineId();
        if (lastMachineId != null && !containsMachineId(machines, lastMachineId)) {
            editor.remove(KEY_LAST_MACHINE_ID);
        }
        editor.apply();
    }

    private boolean containsMachineId(@NonNull List<GhostexMachine> machines,
                                      @NonNull String machineId) {
        for (GhostexMachine machine : machines) {
            if (machine.id.equals(machineId)) return true;
        }
        return false;
    }

    @Nullable
    private String duplicateSshTargetId(@NonNull List<GhostexMachine> machines,
                                        @NonNull String host,
                                        @NonNull String username,
                                        int port,
                                        @Nullable String exceptMachineId) {
        String normalizedHost = normalizeHostForDuplicateCheck(host);
        for (GhostexMachine machine : machines) {
            if (machine.id.equals(exceptMachineId)) continue;
            if (normalizeHostForDuplicateCheck(machine.host).equals(normalizedHost) &&
                machine.username.equals(username) &&
                machine.port == port) {
                return machine.id;
            }
        }
        return null;
    }

    private String normalizeHostForDuplicateCheck(@NonNull String host) {
        return host.trim().toLowerCase(Locale.US);
    }

    private String sshTargetKey(@NonNull String host, @NonNull String username, int port) {
        return normalizeHostForDuplicateCheck(host) + "\u0000" + username + "\u0000" + port;
    }

    private void validatePersistableMachine(@NonNull GhostexMachine machine) {
        if (GhostexMachineValidation.machineIdError(machine.id) != null ||
            GhostexMachineValidation.hostError(machine.host) != null ||
            GhostexMachineValidation.usernameError(machine.username) != null ||
            machine.port <= 0 || machine.port > 65535) {
            throw new IllegalArgumentException("Invalid Ghostex SSH machine cannot be saved: " + machine.id);
        }
    }

}
