package com.termux.app.ghostex;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class GhostexSessionAgentIcon {

    private static final Map<String, Integer> DRAWABLE_BY_ICON = new HashMap<>();
    private static final Map<String, Integer> COLOR_BY_ICON = new HashMap<>();

    static {
        register("amp-cli", R.drawable.ic_ghostex_agent_amp_cli, 0xFFFFFFFF);
        register("antigravity-cli", R.drawable.ic_ghostex_agent_antigravity_cli, 0xFF749BFF);
        register("browser", R.drawable.ic_ghostex_agent_browser, 0xFF82B7FF);
        register("claude", R.drawable.ic_ghostex_agent_claude, 0xFFD97757);
        register("cursor-cli", R.drawable.ic_ghostex_agent_cursor_cli, 0xFFEDECEC);
        register("codex", R.drawable.ic_ghostex_agent_codex, 0xFFFFFFFF);
        register("copilot", R.drawable.ic_ghostex_agent_copilot, 0xFFFFFFFF);
        register("factory-droid", R.drawable.ic_ghostex_agent_factory_droid, 0xFFFF7A1A);
        register("gemini", R.drawable.ic_ghostex_agent_gemini, 0xFF8B9AFF);
        register("grok-build", R.drawable.ic_ghostex_agent_grok_build, 0xFFFFFFFF);
        register("opencode", R.drawable.ic_ghostex_agent_opencode, 0xFF6D96C0);
        register("pi", R.drawable.ic_ghostex_agent_pi, 0xFFC8FF62);
        register("t3", R.drawable.ic_ghostex_agent_t3, 0xFFFF6AF3);
    }

    private GhostexSessionAgentIcon() {}

    /*
    CDXC:AndroidSidebar 2026-05-19-11:05:
    Android session cards should mirror macOS sidebar identity: show the agent
    logo on the left when the Mac inventory exposes agentIcon, otherwise fall
    back to the shared terminal glyph for plain terminal sessions.

    CDXC:AndroidSidebar 2026-05-19-11:05:
    When the CLI omits agentIcon, resolve known default-agent ids and display
    names from the agent field so Codex, Claude, Cursor CLI, and other built-in
    engines still get the correct mask and tint without duplicating macOS logic
    in the adapter.
    */
    @Nullable
    static String resolveIconId(@Nullable String agentIcon, @Nullable String agent) {
        String normalizedIcon = normalizeIconId(agentIcon);
        if (normalizedIcon != null) return normalizedIcon;
        return resolveIconIdFromAgent(agent);
    }

    @DrawableRes
    static int drawableResForSession(@NonNull GhostexRemoteSession session) {
        String iconId = resolveIconId(session.agentIcon, session.agent);
        if (iconId != null) {
            Integer drawable = DRAWABLE_BY_ICON.get(iconId);
            if (drawable != null) return drawable;
        }
        return R.drawable.ic_ghostex_agent_terminal;
    }

    @ColorInt
    static int tintColorForSession(@NonNull GhostexRemoteSession session) {
        String iconId = resolveIconId(session.agentIcon, session.agent);
        if (iconId == null) return GhostexPalette.FOREGROUND;
        Integer color = COLOR_BY_ICON.get(iconId);
        return color == null ? GhostexPalette.FOREGROUND : color;
    }

    @Nullable
    private static String normalizeIconId(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim().toLowerCase(Locale.US);
        return DRAWABLE_BY_ICON.containsKey(normalized) ? normalized : null;
    }

    @Nullable
    private static String resolveIconIdFromAgent(@Nullable String agent) {
        if (agent == null || agent.trim().isEmpty()) return null;
        String normalized = agent.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case "t3":
            case "t3 code":
                return "t3";
            case "codex":
            case "codex cli":
                return "codex";
            case "claude":
            case "claude code":
                return "claude";
            case "cursor":
            case "cursor cli":
            case "cursor agent":
            case "cursor-agent":
                return "cursor-cli";
            case "pi":
            case "pi agent":
            case "π":
                return "pi";
            case "opencode":
            case "open code":
                return "opencode";
            case "gemini":
                return "gemini";
            case "copilot":
            case "github copilot":
                return "copilot";
            case "droid":
            case "factory droid":
                return "factory-droid";
            case "grok":
            case "grok build":
                return "grok-build";
            case "antigravity":
            case "antigravity cli":
            case "agy":
                return "antigravity-cli";
            case "amp":
            case "amp cli":
                return "amp-cli";
            case "browser":
                return "browser";
            default:
                return normalizeIconId(normalized);
        }
    }

    private static void register(@NonNull String iconId, @DrawableRes int drawableRes, @ColorInt int color) {
        DRAWABLE_BY_ICON.put(iconId, drawableRes);
        COLOR_BY_ICON.put(iconId, color);
    }
}
