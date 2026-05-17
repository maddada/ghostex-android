package com.termux.app.ghostex;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class GhostexMachineValidation {

    private GhostexMachineValidation() {}

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-18:46:
    Saved SSH machine entry should reject malformed SSH targets before the app
    attempts reconnect. Shell quoting prevents command injection, but release UX
    still needs clear field errors for whitespace, option-like targets, and
    unsupported user@host shapes that would otherwise surface as opaque SSH
    failures.

    CDXC:AndroidConnectionSecurity 2026-05-17-13:06:
    Saved machines feed automatic reconnect, context-menu SSH actions, and
    known-host repair. Keep accepted hosts to Tailscale/DNS labels, IPv4-style
    addresses, or bracketless IPv6 literals, and keep usernames to SSH-safe
    account tokens so persisted reconnect state cannot carry shell metacharacter
    surprises into those command builders.
    */
    @Nullable
    public static String hostError(@NonNull String host) {
        if (host.isEmpty()) return "Enter the Tailscale host or IP.";
        if (hasWhitespaceOrControl(host)) return "Host cannot contain spaces.";
        if (host.contains("@")) return "Enter only the host or IP, not user@host.";
        if (host.startsWith("-")) return "Host cannot start with a dash.";
        if (host.contains(":")) {
            if (!isIpv6Literal(host)) return "Use a hostname, IPv4 address, or bracketless IPv6 address.";
            return null;
        }
        if (!isDnsLikeHost(host)) return "Use only letters, numbers, dots, and hyphens.";
        return null;
    }

    @Nullable
    public static String usernameError(@NonNull String username) {
        if (username.isEmpty()) return "Enter the SSH username.";
        if (hasWhitespaceOrControl(username)) return "Username cannot contain spaces.";
        if (username.contains("@")) return "Enter only the username.";
        if (username.startsWith("-")) return "Username cannot start with a dash.";
        if (!isSshUsername(username)) return "Use only letters, numbers, dots, underscores, and hyphens.";
        return null;
    }

    @Nullable
    public static String machineIdError(@NonNull String machineId) {
        /*
        CDXC:AndroidConnectionSecurity 2026-05-17-14:57:
        Machine ids are generated internally and reused as credential keys,
        warm-session keys, and last-selected state. Reject persisted ids with
        whitespace, controls, or shell-ish punctuation so corrupt settings
        cannot poison those secondary stores.
        */
        if (machineId.isEmpty()) return "Machine id is missing.";
        if (hasWhitespaceOrControl(machineId)) return "Machine id cannot contain spaces.";
        for (int i = 0; i < machineId.length(); i++) {
            char c = machineId.charAt(i);
            if (!isAsciiLetterOrDigit(c) && c != '.' && c != '_' && c != '-') {
                return "Machine id contains unsupported characters.";
            }
        }
        return null;
    }

    public static int parsePort(@NonNull String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port <= 65535 ? port : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean hasWhitespaceOrControl(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return true;
        }
        return false;
    }

    private static boolean isDnsLikeHost(@NonNull String host) {
        if (host.length() > 253 || host.startsWith(".") || host.endsWith(".") || host.contains("..")) {
            return false;
        }
        String[] labels = host.split("\\.", -1);
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) return false;
            if (label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') return false;
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!isAsciiLetterOrDigit(c) && c != '-') return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(@NonNull String host) {
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c != ':' && !isHexDigit(c)) return false;
        }
        try {
            return InetAddress.getByName(host) instanceof Inet6Address;
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private static boolean isSshUsername(@NonNull String username) {
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if (!isAsciiLetterOrDigit(c) && c != '.' && c != '_' && c != '-') return false;
        }
        return true;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f' || c >= '0' && c <= '9';
    }

}
