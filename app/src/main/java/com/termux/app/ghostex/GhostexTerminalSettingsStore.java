package com.termux.app.ghostex;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class GhostexTerminalSettingsStore {

    private static final String GHOSTEX_PREFS_NAME = "ghostex_terminal_settings";
    private static final String KEY_AUTO_SCROLL_ENABLED = "auto_scroll_enabled";

    private final Context context;
    private final SharedPreferences ghostexPreferences;
    private final TermuxAppSharedPreferences termuxPreferences;
    private final TermuxAppSharedProperties termuxProperties;

    /*
    CDXC:AndroidSettings 2026-05-18-10:42:
    Ghostex Android's sidebar Settings page edits the small set of terminal options users expect to change in-app. Termux properties remain the source of truth for existing terminal behavior, while Ghostex owns the persisted auto-scroll default because upstream only exposed auto-scroll as a session-local extra-key toggle.
    */
    public GhostexTerminalSettingsStore(@NonNull Context context) {
        this.context = context.getApplicationContext();
        ghostexPreferences = this.context.getSharedPreferences(GHOSTEX_PREFS_NAME, Context.MODE_PRIVATE);
        termuxPreferences = TermuxAppSharedPreferences.build(this.context, true);
        termuxProperties = TermuxAppSharedProperties.getProperties();
        ensureHideKeyboardOnStartupDefaultEnabled();
    }

    public boolean isAutoScrollEnabled() {
        return ghostexPreferences.getBoolean(KEY_AUTO_SCROLL_ENABLED, true);
    }

    public void setAutoScrollEnabled(boolean enabled) {
        ghostexPreferences.edit().putBoolean(KEY_AUTO_SCROLL_ENABLED, enabled).apply();
    }

    public boolean isTerminalToolbarVisible() {
        return termuxPreferences != null && termuxPreferences.shouldShowTerminalToolbar();
    }

    public void setTerminalToolbarVisible(boolean visible) {
        if (termuxPreferences != null) termuxPreferences.setShowTerminalToolbar(visible);
    }

    public boolean isSoftKeyboardEnabled() {
        return termuxPreferences != null && termuxPreferences.isSoftKeyboardEnabled();
    }

    public void setSoftKeyboardEnabled(boolean enabled) {
        if (termuxPreferences != null) termuxPreferences.setSoftKeyboardEnabled(enabled);
    }

    public boolean shouldKeepScreenOn() {
        return termuxPreferences != null && termuxPreferences.shouldKeepScreenOn();
    }

    public void setKeepScreenOn(boolean enabled) {
        if (termuxPreferences != null) termuxPreferences.setKeepScreenOn(enabled);
    }

    public int getFontSize() {
        return termuxPreferences == null ? 0 : termuxPreferences.getFontSize();
    }

    public int changeFontSize(boolean increase) {
        if (termuxPreferences == null) return 0;
        termuxPreferences.changeFontSize(increase);
        return termuxPreferences.getFontSize();
    }

    public int getTerminalTranscriptRows() {
        return termuxProperties == null
            ? TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_TRANSCRIPT_ROWS
            : termuxProperties.getTerminalTranscriptRows();
    }

    public void setTerminalTranscriptRows(int rows) throws Exception {
        writeTermuxProperty(TermuxPropertyConstants.KEY_TERMINAL_TRANSCRIPT_ROWS, String.valueOf(rows));
    }

    public boolean isFullscreenEnabled() {
        return termuxProperties != null && termuxProperties.isUsingFullScreen();
    }

    public void setFullscreenEnabled(boolean enabled) throws Exception {
        writeBooleanTermuxProperty(TermuxPropertyConstants.KEY_USE_FULLSCREEN, enabled);
    }

    public boolean shouldHideSoftKeyboardOnStartup() {
        return termuxProperties == null || termuxProperties.shouldSoftKeyboardBeHiddenOnStartup();
    }

    public void setHideSoftKeyboardOnStartup(boolean enabled) throws Exception {
        writeBooleanTermuxProperty(TermuxPropertyConstants.KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP, enabled);
    }

    public boolean shouldOpenUrlOnClick() {
        return termuxProperties != null && termuxProperties.shouldOpenTerminalTranscriptURLOnClick();
    }

    public void setOpenUrlOnClick(boolean enabled) throws Exception {
        writeBooleanTermuxProperty(TermuxPropertyConstants.KEY_TERMINAL_ONCLICK_URL_OPEN, enabled);
    }

    public boolean areTerminalSessionChangeToastsDisabled() {
        return termuxProperties != null && termuxProperties.areTerminalSessionChangeToastsDisabled();
    }

    public void setTerminalSessionChangeToastsDisabled(boolean disabled) throws Exception {
        writeBooleanTermuxProperty(TermuxPropertyConstants.KEY_DISABLE_TERMINAL_SESSION_CHANGE_TOAST, disabled);
    }

    @NonNull
    public String getExtraKeys() {
        return getTermuxProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS);
    }

    public void setExtraKeys(@NonNull String value) throws Exception {
        validateExtraKeys(value, getExtraKeysStyle());
        writeTermuxProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS, value);
    }

    @NonNull
    public String getExtraKeysStyle() {
        return getTermuxProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE,
            TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE);
    }

    public void setExtraKeysStyle(@NonNull String style) throws Exception {
        validateExtraKeys(getExtraKeys(), style);
        writeTermuxProperty(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, style);
    }

    @NonNull
    public String getNightMode() {
        return termuxProperties == null
            ? TermuxPropertyConstants.DEFAULT_IVALUE_NIGHT_MODE
            : termuxProperties.getNightMode();
    }

    public void setNightMode(@NonNull String nightMode) throws Exception {
        writeTermuxProperty(TermuxPropertyConstants.KEY_NIGHT_MODE, nightMode);
    }

    public int getTerminalCursorStyle() {
        return termuxProperties == null
            ? TermuxPropertyConstants.DEFAULT_IVALUE_TERMINAL_CURSOR_STYLE
            : termuxProperties.getTerminalCursorStyle();
    }

    public void setTerminalCursorStyle(@NonNull String style) throws Exception {
        writeTermuxProperty(TermuxPropertyConstants.KEY_TERMINAL_CURSOR_STYLE, style);
    }

    public int getBellBehaviour() {
        return termuxProperties == null
            ? TermuxPropertyConstants.DEFAULT_IVALUE_BELL_BEHAVIOUR
            : termuxProperties.getBellBehaviour();
    }

    public void setBellBehaviour(@NonNull String behaviour) throws Exception {
        writeTermuxProperty(TermuxPropertyConstants.KEY_BELL_BEHAVIOUR, behaviour);
    }

    public boolean areVirtualVolumeKeysEnabled() {
        return termuxProperties == null || !termuxProperties.areVirtualVolumeKeysDisabled();
    }

    public void setVirtualVolumeKeysEnabled(boolean enabled) throws Exception {
        writeTermuxProperty(TermuxPropertyConstants.KEY_VOLUME_KEYS_BEHAVIOUR,
            enabled ? TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VIRTUAL
                : TermuxPropertyConstants.IVALUE_VOLUME_KEY_BEHAVIOUR_VOLUME);
    }

    private void writeTermuxProperty(@NonNull String key, @NonNull String value) throws Exception {
        File file = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create " + parent.getAbsolutePath());
        }

        Properties properties = readEffectiveTermuxProperties();
        properties.setProperty(key, value);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            properties.store(writer, "Ghostex Android terminal settings");
        }
        if (termuxProperties != null) termuxProperties.loadTermuxPropertiesFromDisk();
    }

    private void writeBooleanTermuxProperty(@NonNull String key, boolean value) throws Exception {
        writeTermuxProperty(key, value ? "true" : "false");
    }

    @NonNull
    private String getTermuxProperty(@NonNull String key, @NonNull String defaultValue) {
        String value = termuxProperties == null ? null : termuxProperties.getPropertyValue(key, null, true);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private void validateExtraKeys(@NonNull String extraKeys, @NonNull String style) throws Exception {
        new ExtraKeysInfo(extraKeys, style, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }

    private void ensureHideKeyboardOnStartupDefaultEnabled() {
        try {
            Properties properties = readEffectiveTermuxProperties();
            if (!properties.containsKey(TermuxPropertyConstants.KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP)) {
                /*
                CDXC:AndroidSettings 2026-05-18-11:27:
                Ghostex Android should start with Termux's `hide-soft-keyboard-on-startup` enabled by default while still preserving an explicit user value. Persist the property once when it is absent so startup behavior and Settings UI agree.
                */
                writeTermuxProperty(TermuxPropertyConstants.KEY_HIDE_SOFT_KEYBOARD_ON_STARTUP, "true");
            }
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private Properties readEffectiveTermuxProperties() throws Exception {
        File file = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE.exists()
            ? TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE
            : TermuxConstants.TERMUX_PROPERTIES_SECONDARY_FILE;
        Properties properties = new Properties();
        if (!file.exists()) return properties;
        try (InputStreamReader input = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(input);
        }
        return properties;
    }
}
