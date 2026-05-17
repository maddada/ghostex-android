package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class GhostexMachine {

    public final String id;
    public final String name;
    public final String host;
    public final String username;
    public final int port;
    public final boolean savePassword;
    public final long lastConnectedAt;

    public GhostexMachine(@NonNull String id, @NonNull String name, @NonNull String host,
                          @NonNull String username, int port, boolean savePassword,
                          long lastConnectedAt) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.username = username;
        this.port = port;
        this.savePassword = savePassword;
        this.lastConnectedAt = lastConnectedAt;
    }

    public static GhostexMachine create(@NonNull String name, @NonNull String host,
                                        @NonNull String username, int port,
                                        boolean savePassword) {
        return new GhostexMachine(UUID.randomUUID().toString(), name, host, username, port,
            savePassword, 0L);
    }

    public GhostexMachine withLastConnectedAt(long value) {
        return new GhostexMachine(id, name, host, username, port, savePassword, value);
    }

    public GhostexMachine withSavePassword(boolean value) {
        return new GhostexMachine(id, name, host, username, port, value, lastConnectedAt);
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-12:45:
    Multi-machine switching must stay clear when users save more than one Mac
    or account. Use a single target label for dropdowns, machine cards, and
    action sheets so the UI always shows which SSH host/account will be used.

    CDXC:AndroidConnectionManagement 2026-05-17-14:16:
    Copyable and visible SSH targets must stay unambiguous for IPv6 machines.
    Bracket IPv6 literals in user-facing targets so custom ports do not look
    like another IPv6 segment in Settings, details, action sheets, or clipboard
    copy.
    */
    public String displayLabel() {
        if (!name.trim().isEmpty()) return name;
        return connectionTarget();
    }

    public String connectionTarget() {
        String target = sshDestination();
        return port == 22 ? target : target + ":" + port;
    }

    public String sshDestination() {
        return username + "@" + displayHost();
    }

    private String displayHost() {
        return host.contains(":") ? "[" + host + "]" : host;
    }

    public String dropdownLabel() {
        String label = displayLabel();
        String target = connectionTarget();
        if (label.equals(target)) return label;
        return label + " · " + target;
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-12:54:
    Saved-machine settings should show connection state, not just host strings.
    Expose selected and Last Connected copy from the machine model so settings
    cards can help users choose between multiple Macs without guessing which
    target was last used successfully.
    */
    public String connectionStateLabel(boolean selected, long nowMs) {
        String lastConnected = lastConnectedLabel(nowMs);
        return selected ? "Selected · " + lastConnected : lastConnected;
    }

    public String lastConnectedLabel(long nowMs) {
        if (lastConnectedAt <= 0L) return "Never connected";
        long ageMs = Math.max(0L, nowMs - lastConnectedAt);
        long seconds = Math.max(1L, Math.round(ageMs / 1_000.0));
        if (seconds < 60L) return "Last connected " + seconds + "s ago";
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 60L) return "Last connected " + minutes + "m ago";
        long hours = Math.round(minutes / 60.0);
        if (hours < 48L) return "Last connected " + hours + "h ago";
        long days = Math.round(hours / 24.0);
        return "Last connected " + days + "d ago";
    }

    /*
    CDXC:AndroidConnectionSecurity 2026-05-17-12:50:
    A saved SSH password belongs to the host, username, and port it was saved
    for. If the user edits those target fields, require a fresh password before
    keeping Save password enabled so an old secret is not silently reused for a
    different machine or account.
    */
    public boolean hasSameSshTarget(@NonNull String nextHost, @NonNull String nextUsername, int nextPort) {
        return host.equals(nextHost) && username.equals(nextUsername) && port == nextPort;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("host", host);
        json.put("username", username);
        json.put("port", port);
        json.put("savePassword", savePassword);
        json.put("lastConnectedAt", lastConnectedAt);
        return json;
    }

    @Nullable
    public static GhostexMachine fromJson(@Nullable JSONObject json) {
        if (json == null) return null;
        String id = json.optString("id", "").trim();
        String host = json.optString("host", "").trim();
        String username = json.optString("username", "").trim();
        if (id.isEmpty() || host.isEmpty() || username.isEmpty()) return null;
        /*
        CDXC:AndroidConnectionSecurity 2026-05-17-13:06:
        Automatic reconnect should only restore machines that still pass the
        same SSH target validation as the editor. This prevents older persisted
        records from bypassing the stricter hostname/account contract used by
        remote attach and known-host repair commands.

        CDXC:AndroidConnectionSecurity 2026-05-17-14:15:
        Persisted SSH ports are part of the saved target, not a recoverable
        display preference. Reject invalid stored ports instead of silently
        coercing them to 22 so automatic reconnect never targets a different
        SSH endpoint than the user saved.
        */
        if (GhostexMachineValidation.machineIdError(id) != null ||
            GhostexMachineValidation.hostError(host) != null ||
            GhostexMachineValidation.usernameError(username) != null) {
            return null;
        }

        String name = json.optString("name", "").trim();
        int port = persistedPort(json);
        if (port == -1) return null;

        return new GhostexMachine(id, name, host, username, port,
            json.optBoolean("savePassword", false), json.optLong("lastConnectedAt", 0L));
    }

    private static int persistedPort(@NonNull JSONObject json) {
        if (!json.has("port")) return 22;
        Object rawPort = json.opt("port");
        if (rawPort instanceof Number) {
            double value = ((Number) rawPort).doubleValue();
            if (value != Math.rint(value)) return -1;
            int port = ((Number) rawPort).intValue();
            return port > 0 && port <= 65535 ? port : -1;
        }
        return GhostexMachineValidation.parsePort(String.valueOf(rawPort));
    }

}
