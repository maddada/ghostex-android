package com.termux.app.ghostex;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.terminal.KeyHandler;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GhostexAndroidController {

    private static final String LOG_TAG = "GhostexAndroid";
    private static final int WARM_SESSION_LIMIT = 7;
    private static final long RESUME_RECONNECT_THROTTLE_MS = 15_000L;
    private static final long DRAWER_SESSION_POLL_MS = 4_000L;
    private static final int GHOSTEX_BG = GhostexPalette.BACKGROUND;
    private static final int GHOSTEX_PANEL = GhostexPalette.CARD;
    private static final int GHOSTEX_PANEL_ALT = GhostexPalette.INPUT_BACKGROUND;
    private static final int GHOSTEX_BORDER = GhostexPalette.BORDER;
    private static final int GHOSTEX_TEXT = GhostexPalette.FOREGROUND;
    private static final int GHOSTEX_MUTED = GhostexPalette.MUTED;
    private static final int GHOSTEX_ACCENT = GhostexPalette.BUTTON;
    private static final int GHOSTEX_DANGER = GhostexPalette.DANGER;

    private final TermuxActivity activity;
    private final GhostexMachineStore machineStore;
    private final GhostexPasswordVault passwordVault;
    private final GhostexSessionInventoryClient inventoryClient;
    private final GhostexFileUploadClient fileUploadClient;
    private final GhostexTerminalSettingsStore terminalSettingsStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable drawerSessionPollRunnable = this::runDrawerSessionPoll;
    private final ArrayList<GhostexRemoteSession> remoteSessions = new ArrayList<>();
    private final ArrayList<GhostexDrawerItem> drawerItems = new ArrayList<>();
    private final HashSet<String> collapsedProjectKeys = new HashSet<>();
    private final LinkedHashMap<String, TerminalSession> warmSessions =
        new LinkedHashMap<>(WARM_SESSION_LIMIT + 1, 0.75f, true);
    private final HashMap<String, String> sessionPasswords = new HashMap<>();

    private GhostexRemoteSessionAdapter sessionAdapter;
    private TextView statusView;
    private TextView machinesPageStatusView;
    private TextView settingsPageStatusView;
    private View sessionsPage;
    private View machinesPage;
    private View settingsPage;
    private LinearLayout machinesPageList;
    private LinearLayout settingsPageList;
    private DrawerLayout.DrawerListener drawerSessionPollListener;
    private boolean tutorialShowing;
    private boolean requiredMachineSetupInProgress;
    private boolean drawerSessionPollInFlight;
    private long lastReconnectAttemptAt;
    private long reconnectGeneration;
    private long drawerSessionPollGeneration;
    private long machineCheckGeneration;
    private long remoteActionGeneration;
    private long attachGeneration;
    private int imagePasteCount;
    private int filePasteCount;
    private boolean destroyed;

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-10:13:
    Ghostex Android treats saved SSH machines as the primary app state. Startup
    reconnect, machine switching, onboarding, and failure recovery all flow
    through this controller so Termux remains the terminal substrate instead of
    owning remote-session UX.

    CDXC:AndroidRemoteSessions 2026-05-17-10:13:
    The first release supports ZMX-backed Ghostex sessions only. The session
    inventory filters provider metadata before rendering so unsupported
    tmux/zellij/off sessions do not become selectable Android workflows.

    CDXC:AndroidConnectionManagement 2026-05-17-10:37:
    The saved-machine editor keeps invalid connection details in place with
    field-level errors. A saved-password machine is not accepted unless the
    password is already securely stored or the current edit stores it
    successfully, because startup reconnect depends on that contract.

    CDXC:AndroidConnectionManagement 2026-05-17-10:37:
    The machine dropdown doubles as the connection recovery switcher. It must
    list saved accounts and include an add-machine action so failed reconnects
    can be fixed from the same control without hunting through secondary UI.

    CDXC:AndroidSidebar 2026-05-17-10:43:
    The drawer renders project headers plus session cards, and row action
    surfaces apply to the touched element type. This ports macOS hover controls
    to Android touch without exposing Termux's local-session drawer.

    CDXC:AndroidSidebar 2026-05-18-05:18:
    Project-level actions must be available through the project header's
    three-dot button instead of relying on long-pressing the project name. Keep
    session long-press actions intact while project actions use the visible
    overflow button next to the per-project plus button.

    CDXC:AndroidSidebar 2026-05-18-06:51:
    The sessions sidebar header has a dedicated refresh button immediately left
    of Machines. It reloads the selected machine's ZMX session inventory through
    the same reconnect path as the Refresh sessions action sheet item.

    CDXC:AndroidRemoteSessions 2026-05-18-08:47:
    The sessions page should refresh when the drawer opens and then poll every
    four seconds while it remains visible. Use a separate poll generation so
    background inventory refreshes do not invalidate attach/session action
    callbacks that use reconnect, remote-action, or attach generations.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Android project headers support tap-to-collapse locally, but project ordering
    must remain desktop-owned. Rebuild the drawer from the CLI-provided sidebar
    order and send Move project actions back through the Mac Ghostex CLI.

    CDXC:AndroidOnboarding 2026-05-17-10:51:
    Reconnect used to preflight phone-side SSH packages, but Ghostex Android now
    uses app-owned SSHJ/SFTP for all SSH traffic. Keep Setup focused on
    Tailscale, machine settings, tutorial, and SSHJ host-key repair.

    CDXC:AndroidConnectionSecurity 2026-05-17-11:17:
    Passwords entered without Save password checked are session-only secrets.
    They may be used for the current app process so first-run connection works,
    but they must not be written to SharedPreferences, files, command arguments,
    or logs.

    CDXC:AndroidOnboarding 2026-05-17-11:55:
    First-run setup and machine management should feel like Ghostex product UI,
    not raw Termux settings. Use scrollable step cards, compact machine cards,
    and explicit action rows so the release onboarding is readable on phones.

    CDXC:AndroidConnectionSecurity 2026-05-17-12:06:
    Credential entry should use the same Ghostex-styled UI as onboarding while
    preserving the security boundary: saved passwords go through Keystore and
    unchecked passwords remain process-only for the current app run.

    CDXC:AndroidConnectionManagement 2026-05-17-13:02:
    Drawer recovery states must be readable in the main session-list area, not
    only in the compact status label. Show state cards for first setup,
    connecting, phone-tool repair, no sessions, and failed reconnects so the
    machine dropdown and action buttons have clear context.

    CDXC:AndroidOnboarding 2026-05-17-13:25:
    Phone setup is part of the release onboarding path, not a secondary raw
    settings list. Present it as a Ghostex-styled panel with inline SSH-tool
    status, install repair, Tailscale, and tutorial actions.

    CDXC:AndroidSidebar 2026-05-17-13:48:
    Long-press menus replace macOS hover controls on Android. Render them as
    Ghostex action sheets with descriptions and destructive-action styling so
    session, project, and machine actions feel like part of the release app.

    CDXC:AndroidSidebar 2026-05-17-16:12:
    Action-sheet rows are custom clickable controls. Their accessibility labels
    should include the action name, description, and destructive/tap context so
    long-press workflows remain usable outside visual scanning.

    CDXC:AndroidSidebar 2026-05-17-14:34:
    Session long-press menus should include the macOS focus and rename actions.
    Android sends both through the Ghostex CLI by stable session id so the
    command targets the tapped row, not a mutable visible alias.

    CDXC:AndroidSidebar 2026-05-17-14:58:
    Destructive confirmations and details dialogs are part of the sidebar action
    flow. Use Ghostex-styled panels for these prompts so kill/delete and
    metadata inspection do not drop back to raw Android message dialogs.

    CDXC:AndroidOnboarding 2026-05-17-16:05:
    First launch must hand the user from the setup tutorial into SSH machine
    entry. Later tutorial opens remain informational, but an empty first-run
    app should not leave the user staring at a no-machine drawer state.

    CDXC:AndroidRemoteSessions 2026-05-17-17:34:
    The seven-session warm pool is keyed by machine plus remote session id so
    switching saved Macs never reuses an SSH terminal attached to a different
    host. Deleting a machine also closes its warm attach surfaces.

    CDXC:AndroidRemoteSessions 2026-05-17-16:16:
    Activity recreation must not discard still-running warm SSH attach terminals
    from TermuxService. Rehydrate the warm pool from Ghostex attach command
    metadata before reconnecting so fast switching survives rotation/process UI
    churn without duplicating terminals.

    CDXC:AndroidRemoteSessions 2026-05-17-16:22:
    Rehydration eviction must not drop the current visible attach terminal from
    the warm map just because TermuxService iteration discovers it first. Protect
    the current warm key, then close only oldest non-current overflow entries.

    CDXC:AndroidRemoteSessions 2026-05-17-17:55:
    Remote kill and sleep actions change the lifecycle of the ZMX session on
    the Mac. Close matching warm SSH terminals after those actions succeed so
    Android cannot switch back into a stale attach surface.

    CDXC:AndroidConnectionManagement 2026-05-17-20:06:
    Reconnects and remote actions run asynchronously over SSH. Scope their UI
    effects to the current saved machine so a slow response from a previous
    machine cannot replace the drawer, status, or password prompt after the
    user switches accounts.

    CDXC:AndroidConnectionRecovery 2026-05-17-12:35:
    State cards inside the drawer should be actionable recovery surfaces. Tapping
    a failed/setup/empty state card opens the repair actions users need: retry,
    Tailscale, phone setup, machine management, add machine, and tutorial.

    CDXC:AndroidRemoteSessions 2026-05-17-12:56:
    Project-level wake/sleep/kill can partially succeed. Evict warm terminals
    for successful lifecycle-changing rows and refresh the drawer even when a
    later row fails, so Android does not keep stale ZMX attach surfaces.

    CDXC:AndroidRemoteAttach 2026-05-18-05:02:
    Cold session attaches now use the app-owned SSHJ PTY path, matching
    reconnect, readiness, actions, create, rename, and file upload. Do not
    preflight or require phone-side SSH packages for attach; all phone-side SSH
    now stays inside the app-owned SSHJ transport.

    CDXC:AndroidConnectionManagement 2026-05-17-14:03:
    Saved-machine action sheets should include a non-destructive Check
    connection path. Users managing multiple Macs need to verify SSH
    reachability, credentials, and the remote Ghostex CLI without committing to
    a full machine switch or session-list reconnect.

    CDXC:AndroidConnectionRecovery 2026-05-17-13:33:
    SSH credential recovery must be consistent after startup. If wake, sleep,
    kill, project actions, rename, or machine checks report a missing/rejected
    password, open the same password prompt used by reconnect.

    CDXC:AndroidConnectionManagement 2026-05-17-13:41:
    Check connection is intentionally non-destructive for multi-machine
    management. If a check needs a password, the prompt should save or hold the
    credential and re-run the check without switching the selected machine.

    CDXC:AndroidConnectionRecovery 2026-05-17-13:48:
    Credential recovery should continue the user's interrupted intent. Reconnect
    failures reconnect, Check connection failures re-check, and remote action or
    rename failures retry the same action after the password is accepted.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:05:
    Project-level password recovery should retry only sessions whose action
    failed. Successful wake/sleep/kill rows may already have changed ZMX state
    on the Mac, so repeating them after credential recovery would be noisy and
    potentially destructive.

    CDXC:AndroidConnectionManagement 2026-05-17-13:45:
    Saved-machine actions should expose a copyable SSH target. Multi-device
    Tailscale and Remote Login setup often needs the exact user@host:port value
    outside Ghostex Android, and copying it avoids transcription mistakes.

    CDXC:AndroidConnectionManagement 2026-05-17-13:57:
    Multi-machine settings need an inspectable details surface for support and
    self-diagnosis. The machine action sheet should expose selected state, SSH
    target, password mode, and Last Connected without requiring a reconnect.

    CDXC:AndroidConnectionRecovery 2026-05-17-13:03:
    Host-key repair should be an explicit confirmed action for the selected
    machine. Removing SSHJ's stored host-key fingerprint is local to this phone,
    and the app retries afterward so the verifier can store the current Mac key.

    CDXC:AndroidOnboarding 2026-05-17-13:49:
    Remote sidebar actions are SSH operations too. They now run through the
    same SSHJ transport as reconnect, attach, create, rename, and file upload,
    so they must not depend on phone-side SSH packages.

    CDXC:AndroidConnectionManagement 2026-05-17-13:50:
    Check connection is a repair workflow for SSH reachability, credentials,
    Ghostex CLI, zmx, and the bridge inventory endpoint. It should not block on
    local Termux SSH tools now that SSHJ owns the phone-side transport.

    CDXC:AndroidConnectionManagement 2026-05-17-13:52:
    Saved-machine readiness checks can run while users inspect multiple Macs.
    Scope check callbacks by request generation so a slow older probe cannot
    replace the latest status, setup panel, or credential prompt.

    CDXC:AndroidConnectionManagement 2026-05-17-13:53:
    Check connection callbacks must also prove their saved machine still exists.
    A deleted machine should stay deleted even if an older SSH readiness check
    later reaches password recovery.

    CDXC:AndroidOnboarding 2026-05-17-14:00:
    The required first-run tutorial may launch Tailscale, but that action must
    not dismiss setup. Keep the tutorial open until Add Machine or Done routes
    the user into machine entry or reconnect.

    CDXC:AndroidConnectionManagement 2026-05-17-14:02:
    Machine settings actions should replace the settings panel instead of
    stacking editor, setup, password, action-sheet, or tutorial dialogs on top.
    Keep navigation shallow so saved-machine management remains usable on
    narrow Android screens.

    CDXC:AndroidConnectionManagement 2026-05-17-16:20:
    Saved-machine and setup panels must stay usable on narrow phones. Render
    compact command buttons as two-column rows and allow short labels to wrap
    instead of squeezing four text buttons into one unreadable line.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:04:
    Check connection is machine-specific, so host-key failures should open the
    confirmed Reset SSH host key prompt for the checked machine. This avoids a
    vague setup detour and keeps the destructive host-key repair explicit.

    CDXC:AndroidConnectionRecovery 2026-05-17-14:06:
    Resetting a host key from Check connection must preserve the currently
    selected machine. After the checked machine's SSHJ host-key fingerprint is
    removed, re-run Check connection instead of switching accounts and
    reconnecting.

    CDXC:AndroidOnboarding 2026-05-17-14:09:
    The first-run tutorial should include exact Mac-side verification commands
    for `ghostex` and `zmx`, matching Check connection's readiness probe. This
    lets users confirm the SSH login shell can see both tools before saving the
    Android machine.

    CDXC:AndroidRemoteSessions 2026-05-18-02:31:
    Project headers expose a plus button beside the session-count pill for
    creating a new terminal from Android. The action must call the remote
    Ghostex CLI so the Mac app creates the session with its current zmx
    persistence setting instead of Android creating a local Termux terminal.

    CDXC:AndroidConnectionManagement 2026-05-18-02:58:
    Saved machines, Tailscale, Setup, Retry, and Add live on a dedicated
    in-drawer Machines page. The sessions page keeps only session navigation
    plus a top-right Machines button, while the Machines page has its own
    top-right Sessions button to return without opening a modal.
    */
    public GhostexAndroidController(@NonNull TermuxActivity activity) {
        this.activity = activity;
        machineStore = new GhostexMachineStore(activity);
        passwordVault = new GhostexPasswordVault(activity);
        inventoryClient = new GhostexSessionInventoryClient(activity);
        fileUploadClient = new GhostexFileUploadClient(activity);
        terminalSettingsStore = new GhostexTerminalSettingsStore(activity);
        /*
        CDXC:AndroidRemoteAttach 2026-05-18-05:22:
        Ghostex Android should expose only one shareable diagnostic log even
        before the next remote attach is attempted. Clean legacy multi-file log
        artifacts at controller startup so upgrading the APK fixes the visible
        Downloads folder state immediately.
        */
        GhostexFileLogger.cleanup(activity);
        bindViews();
    }

    public void onTermuxServiceConnected() {
        if (destroyed) return;
        applyAutoScrollSettingToCurrentTerminal();
        refreshMachineControls();
        restoreWarmAttachSessionsFromService();
        if (!machineStore.hasSeenTutorial()) {
            showTutorial(true);
            return;
        }
        reconnectLastMachine(true);
    }

    public void onDestroy() {
        /*
        CDXC:AndroidConnectionManagement 2026-05-17-14:52:
        SSH checks and remote actions may post back after Activity teardown.
        Mark this controller destroyed and advance generations so queued
        callbacks cannot mutate a dead drawer, dialog, or terminal surface.

        CDXC:AndroidConnectionSecurity 2026-05-17-15:17:
        Unchecked Save password means the SSH password is valid only while
        Ghostex Android is open. Clear the process-only credential cache during
        controller teardown so Activity destruction cannot leave plaintext
        session passwords retained longer than the app lifecycle promise.
        */
        destroyed = true;
        reconnectGeneration++;
        machineCheckGeneration++;
        remoteActionGeneration++;
        attachGeneration++;
        drawerSessionPollGeneration++;
        sessionPasswords.clear();
        stopDrawerSessionPolling();
        if (drawerSessionPollListener != null) {
            activity.getDrawer().removeDrawerListener(drawerSessionPollListener);
            drawerSessionPollListener = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    public void notifyTermuxSessionsUpdated() {
        if (destroyed) return;
        evictDeadWarmSessions();
    }

    public void onActivityResumed() {
        if (destroyed) return;
        if (requiredMachineSetupInProgress) return;
        if (!machineStore.hasSeenTutorial()) {
            showTutorial(true);
            return;
        }
        /*
        CDXC:AndroidRemoteAttach 2026-05-18-05:42:
        A foreground warm Ghostex attach is already connected to the selected
        ZMX session. Do not run the automatic resume reconnect while that
        terminal is active; the extra inventory SSH session can race the remote
        attach lifecycle and makes terminal-close diagnosis noisy.
        */
        if (isCurrentWarmRemoteAttachSession()) {
            GhostexFileLogger.log(activity, "connection", null,
                "skipping resume reconnect because current terminal is a warm Ghostex attach");
            return;
        }
        if (machineStore.getLastMachine() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastReconnectAttemptAt < RESUME_RECONNECT_THROTTLE_MS) return;
        reconnectLastMachine(true);
    }

    private boolean isCurrentWarmRemoteAttachSession() {
        TerminalSession currentSession = activity.getCurrentSession();
        if (currentSession == null || !currentSession.isRunning()) return false;
        TermuxService service = activity.getTermuxService();
        if (service == null) return false;
        TermuxSession termuxSession = service.getTermuxSessionForTerminalSession(currentSession);
        if (termuxSession == null || termuxSession.getExecutionCommand() == null) return false;
        return GhostexWarmSessionMetadata.parseAttachCommandLabel(
            termuxSession.getExecutionCommand().commandLabel) != null;
    }

    public void attachFileToCurrentTerminal(@NonNull Uri fileUri) {
        /*
        CDXC:AndroidFileAttach 2026-05-18-04:56:
        The terminal attach button should upload a picked Android image, log,
        or arbitrary file to the selected Mac over SSH and paste a markdown
        reference into the active terminal. Use the current saved machine for
        the upload so the pasted path is readable by the remote agent attached
        in that terminal.
        */
        if (destroyed) return;
        TerminalSession targetSession = activity.getCurrentSession();
        if (targetSession == null || !targetSession.isRunning()) {
            setStatus("Open a remote terminal before attaching a file.");
            activity.showToast("Open a terminal before attaching a file.", false);
            return;
        }
        GhostexMachine machine = machineStore.getLastMachine();
        if (machine == null) {
            setStatus("Add a machine before attaching a file.");
            activity.getDrawer().openDrawer(Gravity.LEFT);
            return;
        }
        if (!canRunRemoteSidebarAction(machine, "attaching a file")) return;

        String password = readPassword(machine);
        long requestGeneration = ++attachGeneration;
        setStatus("Preparing file upload to " + machine.displayLabel() + "...");
        executor.execute(() -> {
            CachedAttachment cachedAttachment = null;
            GhostexFileUploadClient.Result result;
            try {
                cachedAttachment = cachePickedFile(fileUri);
                result = fileUploadClient.uploadFile(machine, password, cachedAttachment.file, cachedAttachment.remotePath);
            } catch (Exception error) {
                result = GhostexFileUploadClient.Result.failure(
                    error.getMessage() == null ? "Could not read the selected file." : error.getMessage());
            } finally {
                if (cachedAttachment != null) cachedAttachment.file.delete();
            }

            GhostexFileUploadClient.Result uploadResult = result;
            boolean isImage = cachedAttachment != null && cachedAttachment.image;
            mainHandler.post(() -> {
                if (!isCurrentAttachRequest(requestGeneration, machine)) return;
                handleFileUploadResult(targetSession, uploadResult, isImage);
            });
        });
    }

    public void refreshCurrentTerminal() {
        /*
        CDXC:AndroidTerminalRefresh 2026-05-18-04:56:
        ZMX documents attach/detach and raw input, but no dedicated dimensions
        refresh command. A mobile refresh button should force Termux's current
        terminal-size calculation and then send PageUp/PageDown through the
        active PTY, matching the reliable redraw nudge used when a ZMX-attached
        CLI paints with stale dimensions.
        */
        if (destroyed) return;
        TerminalSession currentSession = activity.getCurrentSession();
        if (currentSession == null || !currentSession.isRunning()) {
            setStatus("Open a remote terminal before refreshing.");
            activity.showToast("Open a terminal before refreshing.", false);
            return;
        }
        activity.getTerminalView().updateSize();
        TerminalEmulator emulator = currentSession.getEmulator();
        boolean cursorApplicationMode = emulator != null && emulator.isCursorKeysApplicationMode();
        boolean keypadApplicationMode = emulator != null && emulator.isKeypadApplicationMode();
        currentSession.write(KeyHandler.getCode(KeyEvent.KEYCODE_PAGE_UP, 0,
            cursorApplicationMode, keypadApplicationMode));
        currentSession.write(KeyHandler.getCode(KeyEvent.KEYCODE_PAGE_DOWN, 0,
            cursorApplicationMode, keypadApplicationMode));
        setStatus("Refreshed the terminal size and redraw state.");
    }

    private void bindViews() {
        statusView = activity.findViewById(R.id.ghostex_connection_status);
        machinesPageStatusView = activity.findViewById(R.id.ghostex_machines_page_status);
        settingsPageStatusView = activity.findViewById(R.id.ghostex_settings_page_status);
        sessionsPage = activity.findViewById(R.id.ghostex_sessions_page);
        machinesPage = activity.findViewById(R.id.ghostex_machines_page);
        settingsPage = activity.findViewById(R.id.ghostex_settings_page);
        machinesPageList = activity.findViewById(R.id.ghostex_machines_page_list);
        settingsPageList = activity.findViewById(R.id.ghostex_settings_page_list);
        GhostexEdgeToEdgeInsets.applyToReleaseSurface(activity.findViewById(R.id.drawer_layout));
        ListView sessionsList = activity.findViewById(R.id.terminal_sessions_list);
        sessionAdapter = new GhostexRemoteSessionAdapter(activity, drawerItems);
        sessionAdapter.setOnProjectSessionCreateListener(item ->
            createRemoteSessionForProject(item, machineStore.getLastMachine()));
        sessionAdapter.setOnProjectActionsListener(item ->
            showProjectContextMenu(item, machineStore.getLastMachine()));
        sessionAdapter.setOnProjectToggleListener(this::toggleProjectCollapsed);
        sessionsList.setAdapter(sessionAdapter);
        sessionsList.setOnItemClickListener((parent, view, position, id) -> {
            GhostexDrawerItem item = sessionAdapter.getItem(position);
            if (item != null && item.type == GhostexDrawerItem.Type.SESSION && item.session != null) {
                attachRemoteSession(item.session);
            } else if (item != null && item.type == GhostexDrawerItem.Type.STATE_CARD) {
                showRecoveryActions(item);
            }
        });
        sessionsList.setOnItemLongClickListener((parent, view, position, id) -> {
            GhostexDrawerItem item = sessionAdapter.getItem(position);
            if (item != null && item.session != null) {
                showSessionContextMenu(item.session, machineStore.getLastMachine());
                return true;
            } else if (item != null && item.type == GhostexDrawerItem.Type.STATE_CARD) {
                showRecoveryActions(item);
                return true;
            }
            return false;
        });

        View openMachinesButton = activity.findViewById(R.id.ghostex_open_machines_page_button);
        if (openMachinesButton != null) openMachinesButton.setOnClickListener(v -> showMachinesPage());

        View openSettingsButton = activity.findViewById(R.id.ghostex_open_settings_page_button);
        if (openSettingsButton != null) openSettingsButton.setOnClickListener(v -> showSettingsPage());

        View refreshSessionsButton = activity.findViewById(R.id.ghostex_refresh_sessions_button);
        if (refreshSessionsButton != null) refreshSessionsButton.setOnClickListener(v -> reconnectLastMachine(false));

        /*
        CDXC:AndroidNavigation 2026-05-18-04:43:
        Back navigation is reserved for opening the Ghostex sidebar on Android.
        The sidebar's icon-only exit button is the explicit app-exit path.
        */
        View exitButton = activity.findViewById(R.id.ghostex_exit_app_button);
        if (exitButton != null) exitButton.setOnClickListener(v -> activity.finishActivityIfNotFinishing());

        View backToSessionsButton = activity.findViewById(R.id.ghostex_back_to_sessions_button);
        if (backToSessionsButton != null) backToSessionsButton.setOnClickListener(v -> showSessionsPage());

        View settingsBackToSessionsButton = activity.findViewById(R.id.ghostex_settings_back_to_sessions_button);
        if (settingsBackToSessionsButton != null) settingsBackToSessionsButton.setOnClickListener(v -> showSessionsPage());

        View addButton = activity.findViewById(R.id.new_session_button);
        if (addButton != null) addButton.setOnClickListener(v -> showMachineEditor(null));

        View retryButton = activity.findViewById(R.id.ghostex_retry_button);
        if (retryButton != null) retryButton.setOnClickListener(v -> reconnectLastMachine(false));

        View tailscaleButton = activity.findViewById(R.id.ghostex_open_tailscale_button);
        if (tailscaleButton != null) tailscaleButton.setOnClickListener(v -> openTailscale());

        View preparePhoneButton = activity.findViewById(R.id.ghostex_prepare_phone_button);
        if (preparePhoneButton != null) preparePhoneButton.setOnClickListener(v -> showPhoneSetupActions());

        bindDrawerSessionPolling();
    }

    private void showMachinesPage() {
        stopDrawerSessionPolling();
        rebuildMachinesPage();
        if (sessionsPage != null) sessionsPage.setVisibility(View.GONE);
        if (settingsPage != null) settingsPage.setVisibility(View.GONE);
        if (machinesPage != null) machinesPage.setVisibility(View.VISIBLE);
    }

    private void showSettingsPage() {
        stopDrawerSessionPolling();
        rebuildSettingsPage();
        if (sessionsPage != null) sessionsPage.setVisibility(View.GONE);
        if (machinesPage != null) machinesPage.setVisibility(View.GONE);
        if (settingsPage != null) settingsPage.setVisibility(View.VISIBLE);
    }

    private void showSessionsPage() {
        if (machinesPage != null) machinesPage.setVisibility(View.GONE);
        if (settingsPage != null) settingsPage.setVisibility(View.GONE);
        if (sessionsPage != null) sessionsPage.setVisibility(View.VISIBLE);
        if (activity.getDrawer().isDrawerOpen(Gravity.LEFT)) {
            refreshSessionInventoryFromDrawerPoll();
            scheduleDrawerSessionPolling();
        }
    }

    private void bindDrawerSessionPolling() {
        DrawerLayout drawer = activity.getDrawer();
        drawerSessionPollListener = new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                if (drawerView.getId() != R.id.left_drawer) return;
                if (!shouldPollDrawerSessions()) return;
                refreshSessionInventoryFromDrawerPoll();
                scheduleDrawerSessionPolling();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                if (drawerView.getId() != R.id.left_drawer) return;
                stopDrawerSessionPolling();
            }
        };
        drawer.addDrawerListener(drawerSessionPollListener);
        if (drawer.isDrawerOpen(Gravity.LEFT)) {
            refreshSessionInventoryFromDrawerPoll();
            scheduleDrawerSessionPolling();
        }
    }

    private void runDrawerSessionPoll() {
        if (!shouldPollDrawerSessions()) return;
        refreshSessionInventoryFromDrawerPoll();
        scheduleDrawerSessionPolling();
    }

    private void scheduleDrawerSessionPolling() {
        mainHandler.removeCallbacks(drawerSessionPollRunnable);
        if (shouldPollDrawerSessions()) {
            mainHandler.postDelayed(drawerSessionPollRunnable, DRAWER_SESSION_POLL_MS);
        }
    }

    private void stopDrawerSessionPolling() {
        mainHandler.removeCallbacks(drawerSessionPollRunnable);
    }

    private boolean shouldPollDrawerSessions() {
        return !destroyed &&
            sessionsPage != null &&
            sessionAdapter != null &&
            sessionsPage.getVisibility() == View.VISIBLE &&
            activity.getDrawer().isDrawerOpen(Gravity.LEFT) &&
            machineStore.hasSeenTutorial() &&
            machineStore.getLastMachine() != null;
    }

    private void refreshSessionInventoryFromDrawerPoll() {
        if (!shouldPollDrawerSessions() || drawerSessionPollInFlight) return;
        GhostexMachine machine = machineStore.getLastMachine();
        if (machine == null) return;
        drawerSessionPollInFlight = true;
        long requestGeneration = ++drawerSessionPollGeneration;
        String requestMachineId = machine.id;
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexSessionInventoryClient.Result result = inventoryClient.fetchSessions(machine, password);
            mainHandler.post(() -> {
                drawerSessionPollInFlight = false;
                if (isCurrentDrawerSessionPoll(requestGeneration, requestMachineId)) {
                    handleDrawerPollInventoryResult(machine, result);
                }
            });
        });
    }

    private boolean isCurrentDrawerSessionPoll(long requestGeneration, @NonNull String requestMachineId) {
        return shouldPollDrawerSessions() &&
            requestGeneration == drawerSessionPollGeneration &&
            requestMachineId.equals(machineStore.getLastMachineId());
    }

    private void rebuildMachinesPage() {
        if (machinesPageList == null) return;
        machinesPageList.removeAllViews();
        List<GhostexMachine> machines = machineStore.getMachines();
        if (machines.isEmpty()) {
            LinearLayout emptyCard = card();
            addTitle(emptyCard, "No SSH machines yet", 16);
            addBody(emptyCard, "Add the Mac or workstation that runs Ghostex, Tailscale, SSH, and ZMX sessions.");
            addTwoColumnActionRows(emptyCard,
                actionPill("Add", v -> showMachineEditor(null)),
                actionPill("Setup", v -> showPhoneSetupActions()));
            machinesPageList.addView(withBottomMargin(emptyCard, dp(12)));
            return;
        }
        String lastMachineId = machineStore.getLastMachineId();
        for (GhostexMachine machine : machines) {
            machinesPageList.addView(withBottomMargin(drawerMachineCard(
                machine, machine.id.equals(lastMachineId)), dp(12)));
        }
    }

    private void rebuildSettingsPage() {
        if (settingsPageList == null) return;
        settingsPageList.removeAllViews();

        /*
        CDXC:AndroidSettings 2026-05-18-10:42:
        The sidebar Settings page should prioritize Termux's existing config-file options users reach for during remote agent work: fullscreen, startup keyboard state, extra keys, URL tap behavior, session-change toasts, night mode, scrollback, cursor, bell, and volume-key behavior. Keep these controls in the drawer so changing them does not interrupt the current ZMX terminal.
        */
        LinearLayout behaviorCard = card();
        addTitle(behaviorCard, "Terminal behavior", 16);
        addBody(behaviorCard, "Control how the active terminal follows output and handles phone input.");
        behaviorCard.addView(settingCheckBox("Auto scroll", "Follow new terminal output unless text is selected.",
            terminalSettingsStore.isAutoScrollEnabled(), checked -> {
                terminalSettingsStore.setAutoScrollEnabled(checked);
                applyAutoScrollSettingToCurrentTerminal();
                setSettingsStatus(checked ? "Auto scroll is on." : "Auto scroll is paused for the active terminal.");
            }));
        behaviorCard.addView(settingCheckBox("Extra keys toolbar", "Show the Termux extra-keys row above the keyboard.",
            terminalSettingsStore.isTerminalToolbarVisible(), checked -> {
                terminalSettingsStore.setTerminalToolbarVisible(checked);
                activity.getTerminalToolbarViewPager().setVisibility(checked ? View.VISIBLE : View.GONE);
                setSettingsStatus(checked ? "Extra keys toolbar is visible." : "Extra keys toolbar is hidden.");
            }));
        behaviorCard.addView(settingCheckBox("Soft keyboard", "Allow the Android soft keyboard for terminal input.",
            terminalSettingsStore.isSoftKeyboardEnabled(), checked -> {
                terminalSettingsStore.setSoftKeyboardEnabled(checked);
                setSettingsStatus(checked ? "Soft keyboard input is enabled." : "Soft keyboard input is disabled.");
            }));
        behaviorCard.addView(settingCheckBox("Keep screen on", "Prevent the display from sleeping while the terminal is open.",
            terminalSettingsStore.shouldKeepScreenOn(), checked -> {
                terminalSettingsStore.setKeepScreenOn(checked);
                activity.getTerminalView().setKeepScreenOn(checked);
                setSettingsStatus(checked ? "Screen will stay on." : "Screen can sleep normally.");
            }));
        behaviorCard.addView(settingCheckBox("Fullscreen", "Use Termux's fullscreen terminal mode.",
            terminalSettingsStore.isFullscreenEnabled(), checked ->
                setSettingsProperty(() -> terminalSettingsStore.setFullscreenEnabled(checked),
                    checked ? "Fullscreen is on." : "Fullscreen is off.")));
        behaviorCard.addView(settingCheckBox("Hide keyboard on startup", "Start focused on terminal output instead of opening the soft keyboard.",
            terminalSettingsStore.shouldHideSoftKeyboardOnStartup(), checked ->
                setSettingsProperty(() -> terminalSettingsStore.setHideSoftKeyboardOnStartup(checked),
                    checked ? "Keyboard will stay hidden at startup." : "Keyboard may open at startup.")));
        behaviorCard.addView(settingCheckBox("Open URLs on tap", "Tap detected terminal URLs to open them directly.",
            terminalSettingsStore.shouldOpenUrlOnClick(), checked ->
                setSettingsProperty(() -> terminalSettingsStore.setOpenUrlOnClick(checked),
                    checked ? "Terminal URL tap-to-open is on." : "Terminal URL tap-to-open is off.")));
        behaviorCard.addView(settingCheckBox("Disable session change toasts", "Stop showing toast messages when the active terminal session changes.",
            terminalSettingsStore.areTerminalSessionChangeToastsDisabled(), checked ->
                setSettingsProperty(() -> terminalSettingsStore.setTerminalSessionChangeToastsDisabled(checked),
                    checked ? "Session change toasts are disabled." : "Session change toasts are enabled.")));
        settingsPageList.addView(withBottomMargin(behaviorCard, dp(12)));

        LinearLayout extraKeysCard = card();
        addTitle(extraKeysCard, "Extra keys", 16);
        addBody(extraKeysCard, "Edit Termux's `extra-keys` config. Save reloads the toolbar.");
        /*
        CDXC:AndroidSettings 2026-05-18-11:27:
        The extra-keys editor must be prefilled from Termux's current `extra-keys` property, using Termux's out-of-box default when the property is absent. Keep examples one tap away so users can configure this dense property without leaving Settings.
        */
        EditText extraKeysInput = input("Extra keys", terminalSettingsStore.getExtraKeys(),
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        extraKeysInput.setMinLines(4);
        extraKeysInput.setSingleLine(false);
        extraKeysInput.setHorizontallyScrolling(false);
        extraKeysCard.addView(extraKeysInput);
        addTwoColumnActionRows(extraKeysCard,
            actionPill("Save keys", v -> saveExtraKeys(extraKeysInput)),
            actionPill("Examples", v -> showExtraKeysExamples()));
        settingsPageList.addView(withBottomMargin(extraKeysCard, dp(12)));

        LinearLayout displayCard = card();
        addTitle(displayCard, "Display", 16);
        addBody(displayCard, "Choose Termux's built-in extra-key symbol style and app night mode.");
        displayCard.addView(settingDropdown("Extra keys style",
            new String[] { "default", "arrows-only", "arrows-all", "all", "none" },
            terminalSettingsStore.getExtraKeysStyle(),
            value -> setSettingsProperty(() -> terminalSettingsStore.setExtraKeysStyle(value),
                "Extra keys style set to " + value + ".")));
        displayCard.addView(withTopMargin(settingDropdown("Night mode",
            new String[] {
                TermuxPropertyConstants.IVALUE_NIGHT_MODE_SYSTEM,
                TermuxPropertyConstants.IVALUE_NIGHT_MODE_TRUE,
                TermuxPropertyConstants.IVALUE_NIGHT_MODE_FALSE
            },
            terminalSettingsStore.getNightMode(),
            value -> setSettingsProperty(() -> terminalSettingsStore.setNightMode(value),
                "Night mode set to " + value + ".")), dp(10)));
        settingsPageList.addView(withBottomMargin(displayCard, dp(12)));

        LinearLayout fontCard = card();
        int fontSize = terminalSettingsStore.getFontSize();
        addTitle(fontCard, "Font size", 16);
        addBody(fontCard, fontSize == 0 ? "Terminal text size is unavailable." : "Current terminal font size: " + fontSize + " px.");
        addTwoColumnActionRows(fontCard,
            actionPill("Smaller", v -> changeSettingsFontSize(false)),
            actionPill("Larger", v -> changeSettingsFontSize(true)));
        settingsPageList.addView(withBottomMargin(fontCard, dp(12)));

        LinearLayout scrollbackCard = card();
        addTitle(scrollbackCard, "Scrollback", 16);
        addBody(scrollbackCard, "Rows kept for new terminal sessions: " + terminalSettingsStore.getTerminalTranscriptRows() + ".");
        addTwoColumnActionRows(scrollbackCard,
            actionPill("2,000", v -> setSettingsProperty(() -> terminalSettingsStore.setTerminalTranscriptRows(2000), "Scrollback set to 2,000 rows for new terminals.")),
            actionPill("10,000", v -> setSettingsProperty(() -> terminalSettingsStore.setTerminalTranscriptRows(10000), "Scrollback set to 10,000 rows for new terminals.")),
            actionPill("50,000", v -> setSettingsProperty(() -> terminalSettingsStore.setTerminalTranscriptRows(50000), "Scrollback set to 50,000 rows for new terminals.")));
        settingsPageList.addView(withBottomMargin(scrollbackCard, dp(12)));

        LinearLayout cursorCard = card();
        addTitle(cursorCard, "Cursor", 16);
        addBody(cursorCard, "Current cursor style: " + cursorStyleLabel(terminalSettingsStore.getTerminalCursorStyle()) + ".");
        addTwoColumnActionRows(cursorCard,
            actionPill("Block", v -> setSettingsProperty(() -> terminalSettingsStore.setTerminalCursorStyle(TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_BLOCK), "Cursor style set to block.")),
            actionPill("Underline", v -> setSettingsProperty(() -> terminalSettingsStore.setTerminalCursorStyle(TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_UNDERLINE), "Cursor style set to underline.")),
            actionPill("Bar", v -> setSettingsProperty(() -> terminalSettingsStore.setTerminalCursorStyle(TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_BAR), "Cursor style set to bar.")));
        settingsPageList.addView(withBottomMargin(cursorCard, dp(12)));

        LinearLayout alertCard = card();
        addTitle(alertCard, "Alerts and hardware keys", 16);
        addBody(alertCard, "Bell: " + bellBehaviourLabel(terminalSettingsStore.getBellBehaviour()) + ". Volume keys: " +
            (terminalSettingsStore.areVirtualVolumeKeysEnabled() ? "terminal shortcuts" : "phone volume") + ".");
        addTwoColumnActionRows(alertCard,
            actionPill("Vibrate", v -> setSettingsProperty(() -> terminalSettingsStore.setBellBehaviour(TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_VIBRATE), "Bell will vibrate.")),
            actionPill("Beep", v -> setSettingsProperty(() -> terminalSettingsStore.setBellBehaviour(TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_BEEP), "Bell will beep.")),
            actionPill("Ignore bell", v -> setSettingsProperty(() -> terminalSettingsStore.setBellBehaviour(TermuxPropertyConstants.VALUE_BELL_BEHAVIOUR_IGNORE), "Bell will be ignored.")),
            actionPill(terminalSettingsStore.areVirtualVolumeKeysEnabled() ? "Use phone volume" : "Use terminal keys",
                v -> setSettingsProperty(() -> terminalSettingsStore.setVirtualVolumeKeysEnabled(!terminalSettingsStore.areVirtualVolumeKeysEnabled()),
                    terminalSettingsStore.areVirtualVolumeKeysEnabled() ? "Volume buttons now change phone volume." : "Volume buttons now send terminal shortcuts.")));
        settingsPageList.addView(withBottomMargin(alertCard, dp(12)));
    }

    private void changeSettingsFontSize(boolean increase) {
        int fontSize = terminalSettingsStore.changeFontSize(increase);
        if (fontSize > 0) activity.getTerminalView().setTextSize(fontSize);
        setSettingsStatus(fontSize == 0 ? "Font size is unavailable." : "Font size set to " + fontSize + " px.");
        rebuildSettingsPage();
    }

    private void saveExtraKeys(@NonNull EditText extraKeysInput) {
        String value = extraKeysInput.getText().toString().trim();
        if (value.isEmpty()) {
            extraKeysInput.setError("Enter an extra-keys value.");
            return;
        }
        setSettingsProperty(() -> terminalSettingsStore.setExtraKeys(value), "Extra keys saved.");
    }

    private void showExtraKeysExamples() {
        LinearLayout container = verticalContainer(dp(14));
        container.setBackgroundColor(GHOSTEX_BG);
        addTitle(container, "Extra keys examples", 18);
        addBody(container, "Default");
        addBody(container, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS);
        addBody(container, "Single row");
        addBody(container, "[[ESC, TAB, CTRL, ALT, {key: '-', popup: '|'}, DOWN, UP]]");
        addBody(container, "With drawer, paste, keyboard, and scroll buttons");
        addBody(container, "[['ESC','TAB','CTRL','ALT','DRAWER','KEYBOARD'], ['PASTE','SCROLL','LEFT','DOWN','UP','RIGHT']]");

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(container);
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        GhostexDialogStyler.show(dialog, null);
    }

    private void setSettingsProperty(@NonNull SettingsMutation mutation, @NonNull String successStatus) {
        try {
            mutation.run();
            applyReloadedTermuxSettings();
            setSettingsStatus(successStatus);
            rebuildSettingsPage();
        } catch (Exception error) {
            String message = error.getMessage() == null ? "Could not save this setting." : error.getMessage();
            setSettingsStatus(message);
            activity.showToast(message, true);
        }
    }

    private void applyReloadedTermuxSettings() {
        activity.getTerminalView().setTextSize(activity.getPreferences().getFontSize());
        activity.getTerminalView().setKeepScreenOn(activity.getPreferences().shouldKeepScreenOn());
        if (activity.getProperties().isUsingFullScreen()) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        TermuxThemeUtils.setAppNightMode(activity.getProperties().getNightMode());
        AppCompatActivityUtils.setNightMode(activity, NightMode.getAppNightMode().getName(), true);
        activity.getTermuxTerminalViewClient().onReloadProperties();
        activity.reloadTerminalToolbarFromProperties();
        TerminalEmulator emulator = activity.getTerminalView().mEmulator;
        if (emulator != null) emulator.setCursorStyle();
        applyAutoScrollSettingToCurrentTerminal();
    }

    private CheckBox settingCheckBox(@NonNull String title,
                                     @NonNull String summary,
                                     boolean checked,
                                     @NonNull SettingCheckedListener listener) {
        CheckBox checkBox = new CheckBox(activity);
        checkBox.setText(title + "\n" + summary);
        checkBox.setChecked(checked);
        checkBox.setContentDescription(GhostexAccessibilityCopy.join(title, summary, checked ? "On." : "Off.", "Tap to change."));
        styleCheckBox(checkBox);
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onCheckedChanged(isChecked));
        return checkBox;
    }

    private LinearLayout settingDropdown(@NonNull String title,
                                         @NonNull String[] values,
                                         @NonNull String selectedValue,
                                         @NonNull SettingSelectedListener listener) {
        LinearLayout container = verticalContainer(dp(8));
        addTitle(container, title, 14);
        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selectedValue)) {
                selectedIndex = i;
                break;
            }
        }
        spinner.setSelection(selectedIndex, false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String value = values[position];
                if (!value.equals(selectedValue)) listener.onSelected(value);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        container.addView(spinner);
        return container;
    }

    private void applyAutoScrollSettingToCurrentTerminal() {
        TerminalEmulator emulator = activity.getTerminalView().mEmulator;
        if (emulator == null) return;
        emulator.setAutoScrollDisabled(!terminalSettingsStore.isAutoScrollEnabled());
        activity.getTerminalView().onScreenUpdated();
    }

    private void setSettingsStatus(@NonNull String text) {
        if (settingsPageStatusView != null) settingsPageStatusView.setText(text);
    }

    @NonNull
    private String cursorStyleLabel(int style) {
        if (style == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) return "Underline";
        if (style == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) return "Bar";
        return "Block";
    }

    @NonNull
    private String bellBehaviourLabel(int behaviour) {
        if (behaviour == TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP) return "Beep";
        if (behaviour == TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_IGNORE) return "Ignore";
        return "Vibrate";
    }

    private LinearLayout drawerMachineCard(@NonNull GhostexMachine machine, boolean selected) {
        LinearLayout card = card();
        addTitle(card, selected ? machine.displayLabel() + " · Selected" : machine.displayLabel(), 16);
        addBody(card, machine.connectionTarget());
        addBody(card, machine.connectionStateLabel(selected, System.currentTimeMillis()));
        addBody(card, "Tap this card to switch to this machine.");
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(GhostexAccessibilityCopy.join(machine.displayLabel(),
            machine.connectionTarget(), selected ? "Selected machine." : "Tap to switch machines.",
            "Use Edit or Remove for saved-machine management."));
        card.setOnClickListener(v -> connectMachineFromMachinesPage(machine));
        addTwoColumnActionRows(card,
            actionPill("Edit", v -> {
                if (!ensureMachineStillExists(machine, "editing")) return;
                showMachineEditor(machine);
            }),
            actionPill("Remove", v -> confirmDeleteMachine(machine)),
            actionPill("Password", v -> {
                if (!ensureMachineStillExists(machine, "saving credentials")) return;
                showMachinePasswordManager(machine);
            }),
            actionPill("More", v -> {
                if (!ensureMachineStillExists(machine, "opening actions")) return;
                showMachineActions(machine);
            }));
        return card;
    }

    private void connectMachineFromMachinesPage(@NonNull GhostexMachine machine) {
        if (!ensureMachineStillExists(machine, "connecting")) return;
        machineStore.setLastMachineId(machine.id);
        refreshMachineControls();
        reconnectLastMachine(false);
        showSessionsPage();
    }

    private void refreshMachineControls() {
        List<GhostexMachine> machines = machineStore.getMachines();
        Set<String> machineIds = machineIds(machines);
        passwordVault.prunePasswordsForMachineIds(machineIds);
        pruneSessionPasswordsForMachineIds(machineIds);
        rebuildMachinesPage();
        if (machines.isEmpty()) {
            setStatus("Add a machine to connect to Ghostex over Tailscale.");
            setDrawerState("Add your Mac", "Add the macOS machine that runs Ghostex, Tailscale, SSH, and ZMX sessions.", "Open Machines, then tap Add, or open Tutorial from Machines.");
        }
    }

    private Set<String> machineIds(@NonNull List<GhostexMachine> machines) {
        /*
        CDXC:AndroidConnectionSecurity 2026-05-17-15:29:
        The cleaned saved-machine list is the authority for which encrypted SSH
        passwords may remain on this phone. Prune the vault during drawer
        refresh so self-healed invalid records and deleted machines cannot
        leave orphan credentials behind.
        */
        HashSet<String> ids = new HashSet<>();
        for (GhostexMachine machine : machines) ids.add(machine.id);
        return ids;
    }

    private void pruneSessionPasswordsForMachineIds(@NonNull Set<String> machineIds) {
        /*
        CDXC:AndroidConnectionSecurity 2026-05-17-15:40:
        Session-only passwords are process memory only, but they still need to
        follow the cleaned saved-machine list. Remove entries for deleted or
        self-healed-away machines during drawer refresh so orphan credentials
        do not survive until Activity teardown.
        */
        for (String machineId : GhostexMachineManagementPolicy.orphanSessionPasswordMachineIds(
            new ArrayList<>(sessionPasswords.keySet()), machineIds)) {
            sessionPasswords.remove(machineId);
        }
    }

    private void reconnectLastMachine(boolean fromStartup) {
        reconnectLastMachine(fromStartup, null);
    }

    private void reconnectLastMachine(boolean fromStartup, @Nullable String successStatusOverride) {
        GhostexMachine machine = machineStore.getLastMachine();
        if (machine == null) {
            reconnectGeneration++;
            remoteActionGeneration++;
            attachGeneration++;
            sessionAdapter.setCurrentMachineId(null);
            if (!fromStartup) showMachineEditor(null);
            setStatus("Add your Mac or remote workstation to start.");
            setDrawerState("No machine selected", "Ghostex Android needs one saved SSH machine before it can list persistent ZMX sessions.", "Use Add below to save a Mac or workstation.");
            activity.getDrawer().openDrawer(Gravity.LEFT);
            return;
        }
        drawerSessionPollGeneration++;
        long requestGeneration = ++reconnectGeneration;
        remoteActionGeneration++;
        attachGeneration++;
        String requestMachineId = machine.id;
        lastReconnectAttemptAt = System.currentTimeMillis();
        machineStore.setLastMachineId(machine.id);
        sessionAdapter.setCurrentMachineId(machine.id);
        setStatus("Connecting to " + machine.displayLabel() + "...");
        setDrawerState("Connecting", "Opening SSH to " + machine.displayLabel() + " and asking the Ghostex CLI for ZMX-backed sessions.", "Keep Tailscale online on both devices.");
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexSessionInventoryClient.Result result = inventoryClient.fetchSessions(machine, password);
            mainHandler.post(() -> {
                if (isCurrentReconnect(requestGeneration, requestMachineId)) {
                    handleInventoryResult(machine, result, successStatusOverride);
                }
            });
        });
    }

    private boolean isCurrentReconnect(long requestGeneration, @NonNull String requestMachineId) {
        return !destroyed && GhostexAsyncRequestGuard.isCurrentReconnect(requestGeneration, reconnectGeneration,
            requestMachineId, machineStore.getLastMachineId());
    }

    private boolean isCurrentMachine(@NonNull GhostexMachine machine) {
        return !destroyed && GhostexAsyncRequestGuard.isCurrentMachine(machine.id, machineStore.getLastMachineId());
    }

    private boolean isCurrentRemoteAction(long requestGeneration, @NonNull GhostexMachine machine) {
        return GhostexAsyncRequestGuard.isLiveMachineRequest(!destroyed, requestGeneration, remoteActionGeneration,
            machine.id, machineStore.getLastMachineId());
    }

    private boolean isCurrentAttachRequest(long requestGeneration, @NonNull GhostexMachine machine) {
        return GhostexAsyncRequestGuard.isLiveMachineRequest(!destroyed, requestGeneration, attachGeneration,
            machine.id, machineStore.getLastMachineId());
    }

    private boolean ensureMachineStillExists(@NonNull GhostexMachine machine,
                                             @NonNull String actionDescription) {
        /*
        CDXC:AndroidConnectionManagement 2026-05-17-14:40:
        Saved-machine dialogs can outlive the machine card that opened them.
        Guard every machine-scoped mutation/repair before it runs so stale UI
        cannot recreate a deleted account or reconnect an unintended fallback.

        CDXC:AndroidConnectionManagement 2026-05-17-15:23:
        Machine ids stay stable across SSH target edits, so the guard must also
        verify that the saved host/user/port still match the dialog's original
        target. This keeps old credential and host-key recovery panels from
        operating on a newly edited Mac.
        */
        if (GhostexMachineManagementPolicy.canRunExistingMachineAction(machineMatchesSavedTarget(machine))) {
            return true;
        }
        refreshMachineControls();
        setStatus(machine.displayLabel() + " changed or was removed. Open the current machine before " + actionDescription + ".");
        return false;
    }

    private boolean canRunRemoteSidebarAction(@Nullable GhostexMachine machine,
                                              @NonNull String actionDescription) {
        /*
        CDXC:AndroidRemoteSessions 2026-05-17-16:04:
        Session and project action sheets are opened from the current remote
        sidebar but may be accepted after a machine switch or same-id target
        edit. Bind the callback to the opener machine and expire it unless that
        exact SSH target is still selected, preventing stable session ids from
        being sent to the wrong Mac.
        */
        boolean canRun = machine != null && machine.id.equals(machineStore.getLastMachineId()) &&
            machineMatchesSavedTarget(machine);
        if (GhostexMachineManagementPolicy.canRunRemoteSidebarAction(canRun)) {
            return true;
        }
        refreshMachineControls();
        setStatus("Reopen the current session list before " + actionDescription + ".");
        return false;
    }

    private boolean machineMatchesSavedTarget(@NonNull GhostexMachine machine) {
        return currentMatchingMachine(machine) != null;
    }

    @Nullable
    private GhostexMachine currentMatchingMachine(@NonNull GhostexMachine machine) {
        for (GhostexMachine savedMachine : machineStore.getMachines()) {
            if (savedMachine.id.equals(machine.id)) {
                return savedMachine.hasSameSshTarget(machine.host, machine.username, machine.port)
                    ? savedMachine
                    : null;
            }
        }
        return null;
    }

    private void handleInventoryResult(@NonNull GhostexMachine machine,
                                       @NonNull GhostexSessionInventoryClient.Result result,
                                       @Nullable String successStatusOverride) {
        if (!result.ok) {
            remoteSessions.clear();
            setDrawerState("Connection needs attention",
                result.errorMessage == null ? "Could not connect to the selected machine." : result.errorMessage,
                "Use Retry, Tailscale, Setup, or switch machines above.");
            sessionAdapter.notifyDataSetChanged();
            setStatus(result.errorMessage == null ? "Could not connect." : result.errorMessage);
            activity.getDrawer().openDrawer(Gravity.LEFT);
            maybePromptForPasswordAfterFailure(machine, result.errorMessage);
            return;
        }
        applyInventorySessions(machine, result.sessions, successStatusOverride);
        activity.getDrawer().openDrawer(Gravity.LEFT);
    }

    private void handleDrawerPollInventoryResult(@NonNull GhostexMachine machine,
                                                 @NonNull GhostexSessionInventoryClient.Result result) {
        if (!result.ok) {
            String message = result.errorMessage == null ? "Could not refresh sessions." : result.errorMessage;
            setStatus("Session refresh failed: " + message);
            return;
        }
        applyInventorySessions(machine, result.sessions, null);
    }

    private void applyInventorySessions(@NonNull GhostexMachine machine,
                                        @NonNull List<GhostexRemoteSession> sessions,
                                        @Nullable String successStatusOverride) {
        remoteSessions.clear();
        remoteSessions.addAll(sessions);
        sessionAdapter.setCurrentMachineId(machine.id);
        if (sessions.isEmpty()) {
            setDrawerState("No ZMX sessions yet",
                "The machine is reachable, but the Ghostex CLI did not return any ZMX-backed sessions.",
                "Start or resume sessions in Ghostex on the Mac, then tap Retry.");
        } else {
            rebuildDrawerItems();
        }
        sessionAdapter.notifyDataSetChanged();
        GhostexMachine connectedMachine = GhostexMachineManagementPolicy.connectedMachineRecord(
            currentMatchingMachine(machine), System.currentTimeMillis());
        if (connectedMachine != null) machineStore.saveMachine(connectedMachine);
        setStatus(successStatusOverride != null ? successStatusOverride : sessions.isEmpty()
            ? "Connected. No ZMX-backed Ghostex sessions are running."
            : "Connected to " + machine.displayLabel() + " · " + sessions.size() + " ZMX sessions");
    }

    private void attachRemoteSession(@NonNull GhostexRemoteSession remoteSession) {
        GhostexMachine machine = machineStore.getLastMachine();
        attachRemoteSession(machine, remoteSession);
    }

    private void attachRemoteSession(@Nullable GhostexMachine machine,
                                     @NonNull GhostexRemoteSession remoteSession) {
        TermuxService service = activity.getTermuxService();
        if (machine == null || service == null) return;
        if (!canRunRemoteSidebarAction(machine, "attaching to this session")) return;

        String warmKey = GhostexWarmSessionKey.forSession(machine, remoteSession);
        TerminalSession warmSession = warmSessions.get(warmKey);
        String sessionLogTag = zmxSessionLogTag(remoteSession);
        if (warmSession != null && warmSession.isRunning()) {
            GhostexFileLogger.log(activity, "attach", sessionLogTag, "reusing warm session machine=" + machine.displayLabel() +
                " sessionId=" + remoteSession.sessionId + " alias=" + remoteSession.alias);
            sessionAdapter.setActiveSession(machine, remoteSession);
            activity.getTermuxTerminalSessionClient().setCurrentSession(warmSession);
            applyAutoScrollSettingToCurrentTerminal();
            activity.getDrawer().closeDrawers();
            return;
        }

        String password = readPassword(machine);
        setStatus("Preparing SSH attach for " + remoteSession.alias + "...");
        ++attachGeneration;
        openRemoteAttachTerminal(machine, remoteSession, password);
    }

    private void openRemoteAttachTerminal(@NonNull GhostexMachine machine,
                                          @NonNull GhostexRemoteSession remoteSession,
                                          @Nullable String password) {
        TermuxService service = activity.getTermuxService();
        if (service == null) return;
        String sessionLogTag = zmxSessionLogTag(remoteSession);
        if (password == null || password.isEmpty()) {
            GhostexFileLogger.log(activity, "attach", sessionLogTag, "blocked attach because password is missing machine=" +
                machine.displayLabel() + " sessionId=" + remoteSession.sessionId);
            setStatus("Enter the SSH password for " + machine.displayLabel() + " before attaching.");
            showMachinePasswordManager(machine);
            return;
        }
        String remoteCommand = GhostexSshCommandBuilder.attachRemoteCommand(remoteSession);
        String sessionName = remoteSession.alias + " · " + (remoteSession.title.isEmpty() ? "Ghostex" : remoteSession.title);
        GhostexFileLogger.log(activity, "attach", sessionLogTag, "creating remote terminal machine=" + machine.displayLabel() +
            " sessionId=" + remoteSession.sessionId + " alias=" + remoteSession.alias +
            " log=" + GhostexFileLogger.shareableLogPath(activity));
        TermuxSession termuxSession = service.createExternalTerminalSession(
            new GhostexSshAttachProcess(activity, machine, password, remoteCommand, sessionLogTag),
            sessionName,
            GhostexWarmSessionMetadata.buildAttachCommandLabel(machine.id, remoteSession.sessionId),
            sessionLogTag);
        if (termuxSession == null) {
            GhostexFileLogger.log(activity, "attach", sessionLogTag, "service could not create remote terminal machine=" +
                machine.displayLabel() + " sessionId=" + remoteSession.sessionId);
            setStatus("Could not open the remote session.");
            return;
        }
        TerminalSession terminalSession = termuxSession.getTerminalSession();
        String warmKey = GhostexWarmSessionKey.forSession(machine, remoteSession);
        warmSessions.put(warmKey, terminalSession);
        evictOldWarmSessions(terminalSession);
        sessionAdapter.setActiveSession(machine, remoteSession);
        activity.getTermuxTerminalSessionClient().setCurrentSession(terminalSession);
        applyAutoScrollSettingToCurrentTerminal();
        activity.getDrawer().closeDrawers();
        setStatus("Attached to " + remoteSession.alias + " on " + machine.displayLabel() + ". Logs: " +
            GhostexFileLogger.shareableLogPath(activity));
    }

    @NonNull
    private static String zmxSessionLogTag(@NonNull GhostexRemoteSession remoteSession) {
        String alias = remoteSession.alias == null || remoteSession.alias.trim().isEmpty()
            ? "unknown"
            : remoteSession.alias.trim();
        String sessionId = remoteSession.sessionId == null || remoteSession.sessionId.trim().isEmpty()
            ? "missing"
            : remoteSession.sessionId.trim();
        return "zmx=" + alias + " sessionId=" + sessionId;
    }

    private void handleFileUploadResult(@NonNull TerminalSession targetSession,
                                        @NonNull GhostexFileUploadClient.Result result,
                                        boolean isImage) {
        if (!result.ok || result.remotePath == null) {
            String message = result.errorMessage == null ? "Could not upload file." : result.errorMessage;
            setStatus(message);
            activity.showToast(message, true);
            return;
        }
        if (!targetSession.isRunning() || targetSession.getEmulator() == null) {
            setStatus("File uploaded, but the terminal is no longer running.");
            activity.showToast("File uploaded, but the terminal is closed.", true);
            return;
        }
        String label;
        String statusLabel;
        int attachmentNumber;
        if (isImage) {
            label = "Image";
            statusLabel = "image";
            attachmentNumber = ++imagePasteCount;
        } else {
            label = "File";
            statusLabel = "file";
            attachmentNumber = ++filePasteCount;
        }
        String markdown = "[" + label + " #" + attachmentNumber + "](" + result.remotePath + ")";
        targetSession.getEmulator().paste(markdown);
        setStatus("Uploaded " + statusLabel + " #" + attachmentNumber + " to " + result.remotePath + ".");
    }

    @NonNull
    private CachedAttachment cachePickedFile(@NonNull Uri fileUri) throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        String mimeType = resolver.getType(fileUri);
        boolean isImage = mimeType != null && mimeType.startsWith("image/");
        String displayName = pickedFileDisplayName(resolver, fileUri);
        String extension = fileExtension(resolver, fileUri, displayName);
        if (!displayName.contains(".") && !extension.isEmpty()) displayName = displayName + "." + extension;
        String sanitizedName = sanitizeAttachmentFileName(displayName);
        File directory = new File(activity.getCacheDir(), "ghostex-file-upload");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new Exception("Could not prepare file upload cache.");
        }
        File localFile = File.createTempFile("ghostex-file-", "." + extensionOrDefault(extension), directory);
        try (InputStream inputStream = resolver.openInputStream(fileUri);
             FileOutputStream outputStream = new FileOutputStream(localFile)) {
            if (inputStream == null) throw new Exception("Could not open the selected file.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        String remotePath = "/tmp/ghostex-android-attachments/" + System.currentTimeMillis() + "-" + sanitizedName;
        return new CachedAttachment(localFile, remotePath, isImage);
    }

    @NonNull
    private String pickedFileDisplayName(@NonNull ContentResolver resolver, @NonNull Uri fileUri) {
        String displayName = "";
        try (Cursor cursor = resolver.query(fileUri, new String[] { OpenableColumns.DISPLAY_NAME },
            null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) displayName = cursor.getString(columnIndex);
            }
        } catch (Exception ignored) {
            // Some document providers do not expose OpenableColumns metadata.
        }
        if (displayName == null || displayName.trim().isEmpty()) return "attachment";
        return displayName.trim();
    }

    @NonNull
    private String fileExtension(@NonNull ContentResolver resolver,
                                 @NonNull Uri fileUri,
                                 @NonNull String displayName) {
        int dotIndex = displayName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < displayName.length() - 1) {
            String extension = displayName.substring(dotIndex + 1).replaceAll("[^A-Za-z0-9]", "");
            if (!extension.isEmpty()) return extension;
        }
        String mimeType = resolver.getType(fileUri);
        String extension = mimeType == null ? "" : MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return extension == null || extension.trim().isEmpty() ? "bin" : extension.trim();
    }

    @NonNull
    private String sanitizeAttachmentFileName(@NonNull String displayName) {
        String sanitized = displayName.replaceAll("[^A-Za-z0-9._-]", "_");
        sanitized = sanitized.replaceAll("^\\.+", "").replaceAll("_+", "_");
        if (sanitized.isEmpty()) sanitized = "attachment." + extensionOrDefault("");
        return sanitized;
    }

    @NonNull
    private String extensionOrDefault(@Nullable String extension) {
        return extension == null || extension.trim().isEmpty() ? "bin" : extension.trim();
    }

    private void restoreWarmAttachSessionsFromService() {
        TermuxService service = activity.getTermuxService();
        if (service == null) return;
        TerminalSession currentSession = activity.getCurrentSession();
        String currentWarmKey = "";
        for (TermuxSession termuxSession : service.getTermuxSessions()) {
            if (termuxSession == null || termuxSession.getExecutionCommand() == null) continue;
            TerminalSession terminalSession = termuxSession.getTerminalSession();
            if (terminalSession == null || !terminalSession.isRunning()) continue;
            GhostexWarmSessionMetadata.GhostexWarmSessionIds ids =
                GhostexWarmSessionMetadata.parseAttachCommandLabel(termuxSession.getExecutionCommand().commandLabel);
            if (ids == null) continue;
            String warmKey = GhostexWarmSessionKey.forIds(ids.machineId, ids.sessionId);
            warmSessions.put(warmKey, terminalSession);
            if (terminalSession == currentSession) {
                currentWarmKey = warmKey;
                sessionAdapter.setCurrentMachineId(ids.machineId);
                sessionAdapter.setActiveSessionKey(warmKey);
            }
        }
        if (currentWarmKey.isEmpty()) {
            evictOldWarmSessions(currentSession);
        } else {
            evictOverflowWarmSessionsProtecting(currentWarmKey);
        }
    }

    private void rebuildDrawerItems() {
        drawerItems.clear();
        drawerItems.addAll(GhostexDrawerItem.buildItems(remoteSessions, collapsedProjectKeys));
        pruneCollapsedProjectKeys();
    }

    private void toggleProjectCollapsed(@NonNull GhostexDrawerItem projectItem) {
        if (projectItem.type != GhostexDrawerItem.Type.PROJECT_HEADER) return;
        if (collapsedProjectKeys.contains(projectItem.projectKey)) {
            collapsedProjectKeys.remove(projectItem.projectKey);
        } else {
            collapsedProjectKeys.add(projectItem.projectKey);
        }
        rebuildDrawerItems();
        sessionAdapter.notifyDataSetChanged();
    }

    private void pruneCollapsedProjectKeys() {
        HashSet<String> liveProjectKeys = new HashSet<>();
        for (GhostexDrawerItem item : drawerItems) {
            if (item.type == GhostexDrawerItem.Type.PROJECT_HEADER) liveProjectKeys.add(item.projectKey);
        }
        collapsedProjectKeys.retainAll(liveProjectKeys);
    }

    private void setDrawerState(@NonNull String title, @NonNull String body, @NonNull String actionHint) {
        if (destroyed) return;
        drawerItems.clear();
        drawerItems.add(GhostexDrawerItem.stateCard(title, body, actionHint));
        sessionAdapter.notifyDataSetChanged();
    }

    private void evictOldWarmSessions(@Nullable TerminalSession currentSession) {
        while (warmSessions.size() > WARM_SESSION_LIMIT) {
            String oldestKey = warmSessions.keySet().iterator().next();
            TerminalSession oldest = warmSessions.remove(oldestKey);
            if (oldest != null && oldest != currentSession && oldest.isRunning()) {
                oldest.finishIfRunning();
            }
        }
    }

    private void evictOverflowWarmSessionsProtecting(@NonNull String protectedKey) {
        for (String key : GhostexWarmSessionPolicy.overflowWarmSessionKeys(
            warmSessions, WARM_SESSION_LIMIT, protectedKey)) {
            finishWarmSession(key);
        }
    }

    private void evictDeadWarmSessions() {
        List<String> deadKeys = GhostexWarmSessionPolicy.deadWarmSessionKeys(warmSessions,
            session -> session != null && session.isRunning());
        for (String key : deadKeys) warmSessions.remove(key);
    }

    private void finishWarmSessionsForMachine(@NonNull String machineId) {
        ArrayList<String> matchingKeys = new ArrayList<>();
        for (String key : warmSessions.keySet()) {
            if (GhostexWarmSessionKey.belongsToMachine(key, machineId)) matchingKeys.add(key);
        }
        for (String key : matchingKeys) {
            finishWarmSession(key);
        }
    }

    private void evictLifecycleChangedWarmSessions(@NonNull GhostexMachine machine,
                                                   @NonNull String action,
                                                   @NonNull List<GhostexRemoteSession> sessions) {
        if (!shouldCloseWarmSessionForAction(action)) return;
        for (GhostexRemoteSession session : sessions) {
            finishWarmSession(GhostexWarmSessionKey.forSession(machine, session));
        }
    }

    private void evictLifecycleChangedWarmSessions(@NonNull GhostexMachine machine,
                                                   @NonNull String action,
                                                   @NonNull GhostexRemoteSession session) {
        if (!shouldCloseWarmSessionForAction(action)) return;
        finishWarmSession(GhostexWarmSessionKey.forSession(machine, session));
    }

    private boolean shouldCloseWarmSessionForAction(@NonNull String action) {
        return GhostexWarmSessionPolicy.shouldCloseWarmSessionForAction(action);
    }

    private void finishWarmSession(@NonNull String key) {
        TerminalSession session = warmSessions.remove(key);
        if (session != null && session.isRunning()) session.finishIfRunning();
    }

    private void showSessionContextMenu(@NonNull GhostexRemoteSession session,
                                        @Nullable GhostexMachine openerMachine) {
        ArrayList<GhostexAction> actions = new ArrayList<>();
        actions.add(new GhostexAction("Attach", "Open this ZMX session in the terminal.", false, () -> attachRemoteSession(openerMachine, session)));
        actions.add(new GhostexAction("Focus on Mac", "Focus this session in the running Ghostex app.", false, () -> runRemoteSessionAction("focus", openerMachine, session)));
        actions.add(new GhostexAction("Rename", "Update this session title in Ghostex.", false, () -> showRenameSessionPrompt(openerMachine, session)));
        actions.add(new GhostexAction("Wake", "Resume this persistent Ghostex session on the Mac.", false, () -> runRemoteSessionAction("wake", openerMachine, session)));
        actions.add(new GhostexAction("Sleep", "Leave the session persistent but idle on the Mac.", false, () -> runRemoteSessionAction("sleep", openerMachine, session)));
        actions.add(new GhostexAction("Kill", "Stop this remote session on the Mac.", true, () -> confirmKillRemoteSession(openerMachine, session)));
        actions.add(new GhostexAction("Copy attach command", "Copy the SSH command for this session.", false, () -> copyAttachCommand(openerMachine, session)));
        actions.add(new GhostexAction("Refresh sessions", "Reload the ZMX session list from the Mac.", false, () -> {
            if (canRunRemoteSidebarAction(openerMachine, "refreshing sessions")) reconnectLastMachine(false);
        }));
        actions.add(new GhostexAction("Details", "Show provider and project metadata.", false, () -> showSessionDetails(openerMachine, session)));
        showActionSheet(session.title.isEmpty() ? "Ghostex Session" : session.title,
            "Session " + session.alias + " · " + session.displayStatus(), actions);
    }

    private void showProjectContextMenu(@NonNull GhostexDrawerItem projectItem,
                                        @Nullable GhostexMachine openerMachine) {
        /*
        CDXC:AndroidRemoteSessions 2026-05-17-16:16:
        Project action sheets should operate on the project rows the user
        actually long-pressed. Snapshot the current sessions instead of
        recomputing from the mutable drawer later, then still require the
        opener machine to be selected and target-matched when accepted.
        */
        ArrayList<GhostexRemoteSession> projectSessions = sessionsForProject(projectItem.projectKey);
        ArrayList<GhostexAction> actions = new ArrayList<>();
        if (canMoveProject(projectItem, -1)) {
            actions.add(new GhostexAction("Move project up", "Move this project above the previous project in the desktop sidebar.", false,
                () -> runMoveProjectAction("up", openerMachine, projectItem)));
        }
        if (canMoveProject(projectItem, 1)) {
            actions.add(new GhostexAction("Move project down", "Move this project below the next project in the desktop sidebar.", false,
                () -> runMoveProjectAction("down", openerMachine, projectItem)));
        }
        actions.add(new GhostexAction("Refresh sessions", "Reload this project from the Mac.", false, () -> {
            if (canRunRemoteSidebarAction(openerMachine, "refreshing sessions")) reconnectLastMachine(false);
        }));
        actions.add(new GhostexAction("Wake project sessions", "Wake every ZMX session in this project.", false,
            () -> runProjectSessionAction("wake", openerMachine, projectItem, projectSessions)));
        actions.add(new GhostexAction("Sleep project sessions", "Sleep every ZMX session in this project.", false,
            () -> runProjectSessionAction("sleep", openerMachine, projectItem, projectSessions)));
        actions.add(new GhostexAction("Kill project sessions", "Stop all remote sessions in this project.", true,
            () -> confirmKillProjectSessions(openerMachine, projectItem, projectSessions)));
        actions.add(new GhostexAction("Copy project path", "Copy the remote project path.", false,
            () -> copyProjectPath(openerMachine, projectItem)));
        actions.add(new GhostexAction("Details", "Show project session counts.", false,
            () -> showProjectDetails(openerMachine, projectItem)));
        showActionSheet(projectItem.projectTitle,
            projectItem.sessionCount == 1 ? "1 ZMX session" : projectItem.sessionCount + " ZMX sessions", actions);
    }

    private boolean canMoveProject(@NonNull GhostexDrawerItem projectItem, int delta) {
        if (projectItem.projectId.isEmpty()) return false;
        ArrayList<GhostexDrawerItem> projectHeaders = currentProjectHeaders();
        int index = projectHeaderIndex(projectHeaders, projectItem.projectKey);
        int targetIndex = index + delta;
        return index >= 0 && targetIndex >= 0 && targetIndex < projectHeaders.size();
    }

    private ArrayList<GhostexDrawerItem> currentProjectHeaders() {
        ArrayList<GhostexDrawerItem> projectHeaders = new ArrayList<>();
        for (GhostexDrawerItem item : drawerItems) {
            if (item.type == GhostexDrawerItem.Type.PROJECT_HEADER) projectHeaders.add(item);
        }
        return projectHeaders;
    }

    private int projectHeaderIndex(@NonNull List<GhostexDrawerItem> projectHeaders,
                                   @NonNull String projectKey) {
        for (int index = 0; index < projectHeaders.size(); index++) {
            if (projectKey.equals(projectHeaders.get(index).projectKey)) return index;
        }
        return -1;
    }

    private void showRecoveryActions(@NonNull GhostexDrawerItem stateItem) {
        ArrayList<GhostexAction> actions = new ArrayList<>();
        if (machineStore.getLastMachine() != null) {
            actions.add(new GhostexAction("Retry connection", "Reconnect to the selected machine and reload ZMX sessions.", false, () -> reconnectLastMachine(false)));
        }
        actions.add(new GhostexAction("Open Tailscale", "Confirm this phone and the Mac are online in the same tailnet.", false, () -> openTailscale()));
        actions.add(new GhostexAction("Setup", "Review Tailscale, saved machines, tutorial steps, and host-key repair.", false, () -> showPhoneSetupActions()));
        actions.add(new GhostexAction("Add SSH machine", "Save another Mac or workstation for Ghostex Android.", false, () -> showMachineEditor(null)));
        actions.add(new GhostexAction("Manage machines", "Edit saved machines, passwords, and connection targets.", false, () -> showMachineSettings()));
        actions.add(new GhostexAction("Tutorial", "Review the complete setup steps for Mac, phone, Tailscale, SSH, Ghostex CLI, and ZMX.", false, () -> showTutorial(false)));
        showActionSheet(stateItem.stateTitle,
            stateItem.stateActionHint.isEmpty() ? stateItem.stateBody : stateItem.stateActionHint,
            actions);
    }

    private void confirmKillRemoteSession(@Nullable GhostexMachine openerMachine,
                                          @NonNull GhostexRemoteSession session) {
        showConfirmation("Kill remote session?",
            "This stops the selected Ghostex session on the connected machine.",
            session.alias + " · " + blankToDash(session.title),
            "Kill",
            true,
            () -> runRemoteSessionAction("kill", openerMachine, session));
    }

    private void confirmKillProjectSessions(@Nullable GhostexMachine openerMachine,
                                            @NonNull GhostexDrawerItem projectItem,
                                            @NonNull List<GhostexRemoteSession> projectSessions) {
        showConfirmation("Kill project sessions?",
            "This stops " + projectItem.sessionCount + " Ghostex sessions in this project on the connected machine.",
            projectItem.projectTitle,
            "Kill",
            true,
            () -> runProjectSessionAction("kill", openerMachine, projectItem, projectSessions));
    }

    private void runProjectSessionAction(@NonNull String action, @Nullable GhostexMachine machine,
                                         @NonNull GhostexDrawerItem projectItem,
                                         @NonNull List<GhostexRemoteSession> projectSessions) {
        if (!canRunRemoteSidebarAction(machine, "running this project action")) return;
        if (projectSessions.isEmpty()) {
            setStatus("No project sessions are available for " + projectItem.projectTitle + ".");
            return;
        }
        runProjectSessionsAction(action, machine, projectItem.projectTitle, projectSessions);
    }

    private void runProjectSessionsAction(@NonNull String action, @Nullable GhostexMachine machine,
                                          @NonNull String title,
                                          @NonNull List<GhostexRemoteSession> sessions) {
        if (machine == null || sessions.isEmpty()) return;
        if (!canRunRemoteSidebarAction(machine, "running this project action")) return;
        ArrayList<GhostexRemoteSession> actionSessions = new ArrayList<>(sessions);
        long requestGeneration = ++remoteActionGeneration;
        setStatus("Running ghostex " + action + " for " + title + "...");
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexRemoteActionSummary summary = new GhostexRemoteActionSummary();
            for (GhostexRemoteSession session : actionSessions) {
                GhostexSessionInventoryClient.Result result =
                    inventoryClient.runSessionAction(machine, password, action, session);
                summary.record(session, result);
            }
            mainHandler.post(() -> {
                if (!isCurrentRemoteAction(requestGeneration, machine)) return;
                if (summary.hasSuccesses()) {
                    evictLifecycleChangedWarmSessions(machine, action, summary.successfulSessions());
                    reconnectLastMachine(false, summary.hasFailures()
                        ? "Some project sessions changed, but one action failed: " + summary.lastErrorMessage()
                        : GhostexRemoteActionFeedback.projectSuccess(action, title, summary.successfulSessions().size()));
                }
                if (summary.hasFailures()) {
                    String credentialMessage = summary.credentialErrorMessage();
                    if (credentialMessage != null) {
                        setStatus(summary.hasSuccesses()
                            ? "Some project sessions changed. Enter the SSH password to retry the failed rows."
                            : credentialMessage);
                        maybePromptForPasswordAfterFailure(machine, credentialMessage, "Retry",
                            () -> runProjectSessionsAction(action, machine, title, summary.failedSessions()));
                        return;
                    }
                    if (!summary.hasSuccesses()) {
                        String message = summary.lastErrorMessage() == null
                            ? "Project action failed."
                            : summary.lastErrorMessage();
                        setStatus(message);
                    }
                    return;
                }
                if (!summary.hasSuccesses()) setStatus("No project sessions were changed.");
            });
        });
    }

    private void runRemoteSessionAction(@NonNull String action, @NonNull GhostexRemoteSession session) {
        GhostexMachine machine = machineStore.getLastMachine();
        runRemoteSessionAction(action, machine, session);
    }

    private void runRemoteSessionAction(@NonNull String action, @Nullable GhostexMachine machine,
                                        @NonNull GhostexRemoteSession session) {
        if (machine == null) return;
        if (!canRunRemoteSidebarAction(machine, "running this session action")) return;
        long requestGeneration = ++remoteActionGeneration;
        setStatus("Running ghostex " + action + " for " + session.alias + "...");
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexSessionInventoryClient.Result result =
                inventoryClient.runSessionAction(machine, password, action, session);
            mainHandler.post(() -> {
                if (!isCurrentRemoteAction(requestGeneration, machine)) return;
                if (!result.ok) {
                    String message = result.errorMessage == null ? "Remote action failed." : result.errorMessage;
                    setStatus(message);
                    maybePromptForPasswordAfterFailure(machine, message, "Retry",
                        () -> runRemoteSessionAction(action, machine, session));
                    return;
                }
                evictLifecycleChangedWarmSessions(machine, action, session);
                reconnectLastMachine(false, GhostexRemoteActionFeedback.singleSuccess(action, session));
            });
        });
    }

    private void createRemoteSessionForProject(@NonNull GhostexDrawerItem projectItem,
                                               @Nullable GhostexMachine machine) {
        if (machine == null) return;
        if (!canRunRemoteSidebarAction(machine, "creating a session")) return;
        long requestGeneration = ++remoteActionGeneration;
        setStatus("Creating a Ghostex session in " + projectItem.projectTitle + "...");
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexSessionInventoryClient.Result result =
                inventoryClient.createSession(machine, password, projectItem);
            mainHandler.post(() -> {
                if (!isCurrentRemoteAction(requestGeneration, machine)) return;
                if (!result.ok) {
                    String message = result.errorMessage == null ? "Could not create a Ghostex session." : result.errorMessage;
                    setStatus(message);
                    maybePromptForPasswordAfterFailure(machine, message, "Retry",
                        () -> createRemoteSessionForProject(projectItem, machine));
                    return;
                }
                reconnectLastMachine(false, "Created a Ghostex session in " + projectItem.projectTitle + ".");
            });
        });
    }

    private void runMoveProjectAction(@NonNull String direction,
                                      @Nullable GhostexMachine machine,
                                      @NonNull GhostexDrawerItem projectItem) {
        if (machine == null) return;
        if (!canRunRemoteSidebarAction(machine, "moving this project")) return;
        long requestGeneration = ++remoteActionGeneration;
        setStatus("Moving " + projectItem.projectTitle + " " + direction + "...");
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexSessionInventoryClient.Result result =
                inventoryClient.moveProject(machine, password, projectItem, direction);
            mainHandler.post(() -> {
                if (!isCurrentRemoteAction(requestGeneration, machine)) return;
                if (!result.ok) {
                    String message = result.errorMessage == null ? "Could not move this project." : result.errorMessage;
                    setStatus(message);
                    maybePromptForPasswordAfterFailure(machine, message, "Retry",
                        () -> runMoveProjectAction(direction, machine, projectItem));
                    return;
                }
                reconnectLastMachine(false, "Moved " + projectItem.projectTitle + " " + direction + ".");
            });
        });
    }

    private void showRenameSessionPrompt(@Nullable GhostexMachine openerMachine,
                                         @NonNull GhostexRemoteSession session) {
        if (!canRunRemoteSidebarAction(openerMachine, "renaming this session")) return;
        LinearLayout form = verticalContainer(dp(18));
        form.setBackgroundColor(GHOSTEX_BG);
        addTitle(form, "Rename session", 18);
        addBody(form, "This updates the session title in Ghostex on the connected Mac.");

        EditText title = input("Session title", session.title, InputType.TYPE_CLASS_TEXT);
        LinearLayout card = card();
        addTitle(card, "Title", 15);
        card.addView(title);
        form.addView(card);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Rename", null)
            .create();
        GhostexDialogStyler.show(dialog, () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            title.setError(null);
            String cleanTitle = title.getText().toString().trim();
            if (cleanTitle.isEmpty()) {
                title.setError("Enter a session title.");
                return;
            }
            renameRemoteSession(openerMachine, session, cleanTitle);
            dialog.dismiss();
        }));
    }

    private void renameRemoteSession(@NonNull GhostexRemoteSession session, @NonNull String title) {
        GhostexMachine machine = machineStore.getLastMachine();
        renameRemoteSession(machine, session, title);
    }

    private void renameRemoteSession(@Nullable GhostexMachine machine,
                                     @NonNull GhostexRemoteSession session,
                                     @NonNull String title) {
        if (machine == null) return;
        if (!canRunRemoteSidebarAction(machine, "renaming this session")) return;
        long requestGeneration = ++remoteActionGeneration;
        setStatus("Renaming session " + session.alias + "...");
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexSessionInventoryClient.Result result =
                inventoryClient.renameSession(machine, password, session, title);
            mainHandler.post(() -> {
                if (!isCurrentRemoteAction(requestGeneration, machine)) return;
                if (!result.ok) {
                    String message = result.errorMessage == null ? "Could not rename this session." : result.errorMessage;
                    setStatus(message);
                    maybePromptForPasswordAfterFailure(machine, message, "Retry",
                        () -> renameRemoteSession(machine, session, title));
                    return;
                }
                reconnectLastMachine(false, GhostexRemoteActionFeedback.renameSuccess(session));
            });
        });
    }

    private void showSessionDetails(@Nullable GhostexMachine machine,
                                    @NonNull GhostexRemoteSession session) {
        if (!canRunRemoteSidebarAction(machine, "viewing session details")) return;
        ArrayList<GhostexDetail> details = new ArrayList<>();
        details.add(new GhostexDetail("Machine", machine == null ? "Unknown" : machine.dropdownLabel()));
        details.add(new GhostexDetail("Project", blankToDash(session.projectName)));
        details.add(new GhostexDetail("Project path", blankToDash(session.projectPath)));
        details.add(new GhostexDetail("Status", blankToDash(session.displayStatus())));
        details.add(new GhostexDetail("Focused on Mac", session.isFocused ? "Yes" : "No"));
        details.add(new GhostexDetail("Last active", GhostexSessionCardFormatter.formatLastActiveDetail(
            session.lastInteractionAt, System.currentTimeMillis())));
        details.add(new GhostexDetail("Provider", "zmx"));
        details.add(new GhostexDetail("ZMX session", blankToDash(session.providerSessionName)));
        details.add(new GhostexDetail("Agent", blankToDash(session.agent)));
        details.add(new GhostexDetail("Session id", blankToDash(session.sessionId)));
        showDetailsPanel(session.alias + " · " + blankToDash(session.title),
            "Remote session metadata from the Ghostex CLI.", details);
    }

    private void copyAttachCommand(@Nullable GhostexMachine machine,
                                   @NonNull GhostexRemoteSession session) {
        if (machine == null) return;
        if (!canRunRemoteSidebarAction(machine, "copying this attach command")) return;
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Ghostex attach command",
            GhostexSshCommandBuilder.buildCopyableAttachCommand(machine, session)));
        activity.showToast("Attach command copied", false);
    }

    private void copyProjectPath(@Nullable GhostexMachine machine,
                                 @NonNull GhostexDrawerItem projectItem) {
        if (!canRunRemoteSidebarAction(machine, "copying this project path")) return;
        if (projectItem.projectPath.isEmpty()) {
            activity.showToast("No project path available", false);
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Ghostex project path", projectItem.projectPath));
        activity.showToast("Project path copied", false);
    }

    private void showProjectDetails(@Nullable GhostexMachine machine,
                                    @NonNull GhostexDrawerItem projectItem) {
        if (!canRunRemoteSidebarAction(machine, "viewing project details")) return;
        ArrayList<GhostexDetail> details = new ArrayList<>();
        details.add(new GhostexDetail("Path", blankToDash(projectItem.projectPath)));
        details.add(new GhostexDetail("Sessions", String.valueOf(projectItem.sessionCount)));
        details.add(new GhostexDetail("Working", String.valueOf(projectItem.workingCount)));
        details.add(new GhostexDetail("Attention", String.valueOf(projectItem.attentionCount)));
        details.add(new GhostexDetail("Sleeping", String.valueOf(projectItem.sleepingCount)));
        showDetailsPanel(projectItem.projectTitle, "Project summary from the remote sidebar list.", details);
    }

    private ArrayList<GhostexRemoteSession> sessionsForProject(@NonNull String projectKey) {
        ArrayList<GhostexRemoteSession> result = new ArrayList<>();
        for (GhostexDrawerItem item : drawerItems) {
            if (item.type == GhostexDrawerItem.Type.SESSION && projectKey.equals(item.projectKey) && item.session != null) {
                result.add(item.session);
            }
        }
        return result;
    }

    private View buildTutorialView() {
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);

        LinearLayout container = verticalContainer(dp(18));
        container.setBackgroundColor(GHOSTEX_BG);

        /*
        CDXC:AndroidSidebar 2026-05-17-17:44:
        Dialog titles should live inside the Ghostex-styled content, not in
        platform AlertDialog chrome. Keep the required first-run smoke text
        visible here while avoiding a mismatched native title band above the
        dark onboarding panel.
        */
        addTitle(container, "Set up Ghostex Android", 18);
        addTitle(container, GhostexOnboardingGuide.TITLE, 18);
        addBody(container, GhostexOnboardingGuide.INTRO);

        String[] steps = GhostexOnboardingGuide.steps();
        for (int i = 0; i < steps.length; i++) {
            addStepCard(container, i + 1, steps[i]);
        }
        scrollView.addView(container);
        return scrollView;
    }

    private View buildMachineSettingsView(@NonNull List<GhostexMachine> machines,
                                          @NonNull AlertDialog[] settingsDialog) {
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout container = verticalContainer(dp(18));
        container.setBackgroundColor(GHOSTEX_BG);

        addTitle(container, "Saved machines", 18);
        addBody(container, machines.isEmpty()
            ? "Add your Mac or remote workstation to connect to persistent ZMX sessions."
            : "Choose a machine to reconnect, edit credentials, or manage saved password state.");

        if (machines.isEmpty()) {
            LinearLayout emptyCard = card();
            addTitle(emptyCard, "No SSH machines yet", 16);
            addBody(emptyCard, "Use Add machine after setting up Tailscale, macOS Remote Login, Ghostex CLI, and zmx persistence.");
            container.addView(withBottomMargin(emptyCard, dp(12)));
        } else {
            String lastMachineId = machineStore.getLastMachineId();
            for (GhostexMachine machine : machines) {
                container.addView(withBottomMargin(machineCard(machine, machine.id.equals(lastMachineId), settingsDialog), dp(12)));
            }
        }

        addTitle(container, "Setup", 15);
        addTwoColumnActionRows(container,
            actionPill("Add", v -> {
            dismissDialog(settingsDialog);
            showMachineEditor(null);
            }),
            actionPill("Setup", v -> {
            dismissDialog(settingsDialog);
            showPhoneSetupActions();
            }),
            actionPill("Tailscale", v -> {
            dismissDialog(settingsDialog);
            openTailscale();
            }),
            actionPill("Tutorial", v -> {
            dismissDialog(settingsDialog);
            showTutorial(false);
            }));

        scrollView.addView(container);
        return scrollView;
    }

    private LinearLayout machineCard(@NonNull GhostexMachine machine, boolean selected,
                                     @NonNull AlertDialog[] settingsDialog) {
        LinearLayout card = card();
        addTitle(card, machine.displayLabel(), 16);
        addBody(card, machine.connectionTarget());
        addBody(card, machine.connectionStateLabel(selected, System.currentTimeMillis()));
        addBody(card, machine.savePassword && passwordVault.hasPassword(machine.id)
            ? "Saved password enabled"
            : "Uses SSH keys, Tailscale SSH, or session-only password");

        addTwoColumnActionRows(card,
            actionPill("Connect", v -> {
            dismissDialog(settingsDialog);
            if (!ensureMachineStillExists(machine, "connecting")) return;
            machineStore.setLastMachineId(machine.id);
            refreshMachineControls();
            reconnectLastMachine(false);
            }),
            actionPill("Password", v -> {
            dismissDialog(settingsDialog);
            if (!ensureMachineStillExists(machine, "saving credentials")) return;
            showMachinePasswordManager(machine);
            }),
            actionPill("Edit", v -> {
            dismissDialog(settingsDialog);
            if (!ensureMachineStillExists(machine, "editing")) return;
            showMachineEditor(machine);
            }),
            actionPill("More", v -> {
            dismissDialog(settingsDialog);
            if (!ensureMachineStillExists(machine, "opening actions")) return;
            showMachineActions(machine);
            }));
        return card;
    }

    private void showTutorial(boolean requiredFirstRun) {
        if (tutorialShowing) return;
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(buildTutorialView())
            .setNegativeButton("Open Tailscale", (clickedDialog, which) -> openTailscale())
            .setPositiveButton("Add machine", (clickedDialog, which) -> {
                markTutorialSeenFromTutorialAction(requiredFirstRun);
                showMachineEditor(null, requiredFirstRun && machineStore.getMachines().isEmpty());
            })
            .setNeutralButton("Done", (clickedDialog, which) -> {
                markTutorialSeenFromTutorialAction(requiredFirstRun);
                continueAfterTutorial();
            })
            .create();
        dialog.setOnDismissListener(ignored -> tutorialShowing = false);
        dialog.setCancelable(!requiredFirstRun);
        GhostexDialogStyler.show(dialog, () -> {
            tutorialShowing = true;
            if (requiredFirstRun) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> openTailscale());
            }
        });
    }

    private void markTutorialSeenFromTutorialAction(boolean requiredFirstRun) {
        if (GhostexOnboardingCompletionPolicy.shouldMarkTutorialSeenFromTutorialAction(
            requiredFirstRun, !machineStore.getMachines().isEmpty())) {
            machineStore.setTutorialSeen(true);
        }
    }

    private void continueAfterTutorial() {
        if (machineStore.getMachines().isEmpty()) {
            showMachineEditor(null, !machineStore.hasSeenTutorial());
            return;
        }
        reconnectLastMachine(true);
    }

    private void showMachineSettings() {
        showMachinesPage();
    }

    private void showMachineActions(@NonNull GhostexMachine machine) {
        ArrayList<GhostexAction> actions = new ArrayList<>();
        actions.add(new GhostexAction("Connect", "Use this machine for the next reconnect.", false, () -> {
            if (!ensureMachineStillExists(machine, "connecting")) return;
            machineStore.setLastMachineId(machine.id);
            refreshMachineControls();
            reconnectLastMachine(false);
        }));
        actions.add(new GhostexAction("Check connection", "Verify SSH reachability, credentials, Ghostex CLI, and zmx.", false,
            () -> {
                if (!ensureMachineStillExists(machine, "checking connection")) return;
                checkMachineConnection(machine);
            }));
        actions.add(new GhostexAction("Enter password", "Use a saved or session-only SSH password.", false,
            () -> {
                if (!ensureMachineStillExists(machine, "saving credentials")) return;
                showMachinePasswordManager(machine);
            }));
        actions.add(new GhostexAction("Edit", "Update display name, host, user, port, or password mode.", false, () -> {
            if (!ensureMachineStillExists(machine, "editing")) return;
            showMachineEditor(machine);
        }));
        actions.add(new GhostexAction("Details", "Show selected state, SSH target, password mode, and last connection.", false,
            () -> {
                if (!ensureMachineStillExists(machine, "viewing details")) return;
                showMachineDetails(machine);
            }));
        actions.add(new GhostexAction("Copy SSH target", "Copy the exact user@host:port value for setup or support.", false,
            () -> {
                if (!ensureMachineStillExists(machine, "copying its SSH target")) return;
                copyMachineTarget(machine);
            }));
        if (passwordVault.hasPassword(machine.id) || machine.savePassword) {
            actions.add(new GhostexAction("Forget saved password", "Keep the machine but remove its stored password.", true, () -> confirmForgetSavedPassword(machine)));
        }
        actions.add(new GhostexAction("Reset SSH host key", "Remove this phone's saved SSH host key for this machine, then retry.", true,
            () -> confirmResetKnownHost(machine)));
        actions.add(new GhostexAction("Delete", "Remove this machine from Ghostex Android.", true, () -> confirmDeleteMachine(machine)));
        actions.add(new GhostexAction("Open Tailscale", "Check whether this phone and the Mac are online.", false, () -> openTailscale()));
        showActionSheet(machine.displayLabel(), machine.connectionTarget(), actions);
    }

    private void checkMachineConnection(@NonNull GhostexMachine machine) {
        if (!ensureMachineStillExists(machine, "checking connection")) return;
        long requestGeneration = ++machineCheckGeneration;
        setStatus("Checking " + machine.displayLabel() + "...");
        executor.execute(() -> {
            String password = readPassword(machine);
            GhostexSessionInventoryClient.Result result = inventoryClient.checkConnection(machine, password);
            mainHandler.post(() -> {
                if (!isLatestMachineCheck(requestGeneration, machine)) return;
                if (!result.ok) {
                    setStatus(result.errorMessage == null ? "Machine check failed." : result.errorMessage);
                    maybePromptForHostKeyResetAfterCheckFailure(machine, result.errorMessage);
                    maybePromptForPasswordAfterCheckFailure(machine, result.errorMessage);
                    return;
                }
                setStatus(machine.displayLabel() + " is ready for Ghostex Android.");
            });
        });
    }

    private boolean isLatestMachineCheck(long requestGeneration, @NonNull GhostexMachine machine) {
        return !destroyed &&
            GhostexAsyncRequestGuard.isLatestRequest(requestGeneration, machineCheckGeneration) &&
            machineStore.hasMachine(machine.id);
    }

    private void showMachineDetails(@NonNull GhostexMachine machine) {
        ArrayList<GhostexDetail> details = new ArrayList<>();
        details.add(new GhostexDetail("Display name", machine.displayLabel()));
        details.add(new GhostexDetail("SSH target", machine.connectionTarget()));
        details.add(new GhostexDetail("Host", machine.host));
        details.add(new GhostexDetail("Username", machine.username));
        details.add(new GhostexDetail("Port", String.valueOf(machine.port)));
        details.add(new GhostexDetail("Selected", machine.id.equals(machineStore.getLastMachineId()) ? "Yes" : "No"));
        details.add(new GhostexDetail("Last connected", machine.lastConnectedLabel(System.currentTimeMillis())));
        details.add(new GhostexDetail("Password mode", machinePasswordMode(machine)));
        details.add(new GhostexDetail("Machine id", machine.id));
        showDetailsPanel(machine.displayLabel(), "Saved SSH machine metadata on this phone.", details);
    }

    private void copyMachineTarget(@NonNull GhostexMachine machine) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Ghostex SSH target", machine.connectionTarget()));
        activity.showToast("SSH target copied", false);
    }

    private String machinePasswordMode(@NonNull GhostexMachine machine) {
        if (machine.savePassword && passwordVault.hasPassword(machine.id)) {
            return "Saved in Android Keystore";
        }
        if (sessionPasswords.containsKey(machine.id)) {
            return "Session-only password";
        }
        if (machine.savePassword) {
            return "Save password enabled, but no password is stored";
        }
        return "SSH keys, Tailscale SSH, or prompt when needed";
    }

    private void confirmForgetSavedPassword(@NonNull GhostexMachine machine) {
        showConfirmation("Forget saved password?",
            "Ghostex Android will keep the machine entry, but reconnect will require SSH keys, Tailscale SSH, or a password entered for this app session.",
            machine.displayLabel(),
            "Forget",
            true,
            () -> {
                if (!ensureMachineStillExists(machine, "forgetting its password")) return;
                GhostexMachine currentMachine = currentMatchingMachine(machine);
                if (currentMachine == null) return;
                passwordVault.deletePassword(machine.id);
                sessionPasswords.remove(machine.id);
                /*
                CDXC:AndroidConnectionManagement 2026-05-17-15:25:
                Credential dialogs can outlive non-target edits such as display
                name or Last Connected changes. Rebase password-mode writes on
                the current saved machine record so accepting an older prompt
                does not roll back newer machine metadata.
                */
                machineStore.saveMachine(currentMachine.withSavePassword(false));
                refreshMachineControls();
                setStatus("Saved password removed for " + currentMachine.displayLabel() + ".");
            });
    }

    private void confirmDeleteMachine(@NonNull GhostexMachine machine) {
        showConfirmation("Delete SSH machine?",
            "This removes the machine from Ghostex Android on this device. Remote Ghostex sessions on the Mac are not changed.",
            machine.displayLabel(),
            "Delete",
            true,
            () -> {
                /*
                CDXC:AndroidConnectionManagement 2026-05-17-15:52:
                Delete confirmations are stale-prone destructive UI. They must
                re-check the saved machine's current SSH target at accept time
                so an older dialog cannot delete a machine after the user edits
                host, username, or port under the same stable id.
                */
                if (!GhostexMachineManagementPolicy.canAcceptDestructiveMachineConfirmation(
                    machineMatchesSavedTarget(machine))) {
                    refreshMachineControls();
                    setStatus(machine.displayLabel() + " changed or was removed. Open the current machine before deleting it.");
                    return;
                }
                boolean shouldReconnect = GhostexMachineManagementPolicy.shouldReconnectAfterDelete(
                    machine.id, machineStore.getLastMachineId());
                passwordVault.deletePassword(machine.id);
                sessionPasswords.remove(machine.id);
                finishWarmSessionsForMachine(machine.id);
                sessionAdapter.clearActiveSessionForMachine(machine.id);
                machineStore.deleteMachine(machine.id);
                refreshMachineControls();
                if (shouldReconnect) {
                    reconnectLastMachine(false);
                } else {
                    setStatus("Deleted " + machine.displayLabel() + ". The active machine was not changed.");
                }
            });
    }

    private void confirmResetKnownHost(@NonNull GhostexMachine machine) {
        showConfirmation("Reset SSH host key?",
            "This removes only this phone's saved SSH host key for the selected machine. Use it after confirming the Mac is the correct device.",
            machine.connectionTarget(),
            "Reset",
            true,
            () -> resetKnownHost(machine));
    }

    private void confirmResetKnownHostForCheck(@NonNull GhostexMachine machine) {
        showConfirmation("Reset SSH host key?",
            "This removes only this phone's saved SSH host key for the checked machine, then runs Check connection again without switching accounts.",
            machine.connectionTarget(),
            "Reset",
            true,
            () -> resetKnownHostForCheck(machine));
    }

    private void showPhoneSetupActions() {
        final AlertDialog[] setupDialog = new AlertDialog[1];
        LinearLayout container = verticalContainer(dp(18));
        container.setBackgroundColor(GHOSTEX_BG);

        addTitle(container, "Setup", 18);
        addBody(container, "Ghostex Android connects with its built-in SSH transport, then asks the Mac-hosted Ghostex CLI for ZMX sessions. No phone-side SSH package install is required.");

        LinearLayout transportCard = card();
        addTitle(transportCard, "Connection path", 15);
        addBody(transportCard, "The phone uses SSHJ inside the app for reconnect, attach, actions, create, rename, and file upload. The Mac still needs Remote Login, Ghostex CLI, zmx, and Tailscale online.");
        ArrayList<View> transportActions = new ArrayList<>();
        GhostexMachine selectedMachine = machineStore.getLastMachine();
        if (selectedMachine != null) {
            transportActions.add(actionPill("Check", v -> {
                if (setupDialog[0] != null) setupDialog[0].dismiss();
                checkMachineConnection(selectedMachine);
            }));
            transportActions.add(actionPill("Host key", v -> {
                if (setupDialog[0] != null) setupDialog[0].dismiss();
                confirmResetKnownHost(selectedMachine);
            }));
        }
        transportActions.add(actionPill("Machines", v -> {
            if (setupDialog[0] != null) setupDialog[0].dismiss();
            showMachineSettings();
        }));
        addTwoColumnActionRows(transportCard, transportActions.toArray(new View[0]));
        container.addView(withBottomMargin(transportCard, dp(12)));

        LinearLayout networkCard = card();
        addTitle(networkCard, "Network setup", 15);
        addBody(networkCard, "Tailscale should be installed on this phone and on the Mac, signed into the same tailnet, and online before you retry connection.");
        ArrayList<View> networkActions = new ArrayList<>();
        networkActions.add(actionPill("Tailscale", v -> {
            if (setupDialog[0] != null) setupDialog[0].dismiss();
            openTailscale();
        }));
        networkActions.add(actionPill("Tutorial", v -> {
            if (setupDialog[0] != null) setupDialog[0].dismiss();
            showTutorial(false);
        }));
        addTwoColumnActionRows(networkCard, networkActions.toArray(new View[0]));
        container.addView(networkCard);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        setupDialog[0] = dialog;
        GhostexDialogStyler.show(dialog);
    }

    private void showActionSheet(@NonNull String title, @NonNull String subtitle,
                                 @NonNull List<GhostexAction> actions) {
        final AlertDialog[] actionDialog = new AlertDialog[1];
        ScrollView scrollView = new ScrollView(activity);
        LinearLayout container = verticalContainer(dp(18));
        container.setBackgroundColor(GHOSTEX_BG);
        addTitle(container, title, 18);
        if (!subtitle.trim().isEmpty()) addBody(container, subtitle);

        for (GhostexAction action : actions) {
            container.addView(withBottomMargin(actionRow(action, () -> {
                if (actionDialog[0] != null) actionDialog[0].dismiss();
                action.run();
            }), dp(8)));
        }
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        actionDialog[0] = dialog;
        GhostexDialogStyler.show(dialog);
    }

    private void dismissDialog(@NonNull AlertDialog[] dialogHolder) {
        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
    }

    private void showConfirmation(@NonNull String title, @NonNull String body,
                                  @NonNull String target, @NonNull String confirmLabel,
                                  boolean destructive, @NonNull Runnable onConfirm) {
        LinearLayout container = verticalContainer(dp(18));
        container.setBackgroundColor(GHOSTEX_BG);
        addTitle(container, title, 18);
        addBody(container, body);

        LinearLayout card = card();
        addTitle(card, target, 15);
        TextView impact = bodyText(destructive
            ? "This action changes the connected Mac or this device's saved connection state."
            : "Confirm to continue.");
        impact.setTextColor(destructive ? GHOSTEX_DANGER : GHOSTEX_MUTED);
        card.addView(impact);
        container.addView(card);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(confirmLabel, (clickedDialog, which) -> onConfirm.run())
            .create();
        if (destructive) {
            GhostexDialogStyler.showDestructive(dialog, null);
        } else {
            GhostexDialogStyler.show(dialog);
        }
    }

    private void showDetailsPanel(@NonNull String title, @NonNull String subtitle,
                                  @NonNull List<GhostexDetail> details) {
        LinearLayout container = verticalContainer(dp(18));
        container.setBackgroundColor(GHOSTEX_BG);
        addTitle(container, title, 18);
        addBody(container, subtitle);

        LinearLayout card = card();
        for (GhostexDetail detail : details) {
            card.addView(withBottomMargin(detailRow(detail), dp(8)));
        }
        container.addView(card);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .create();
        GhostexDialogStyler.show(dialog);
    }

    private View actionRow(@NonNull GhostexAction action, @NonNull Runnable onClick) {
        LinearLayout row = verticalContainer(dp(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(panelBackground(GHOSTEX_PANEL_ALT));
        row.setOnClickListener(view -> onClick.run());
        row.setContentDescription(GhostexAccessibilityCopy.join(action.label, action.detail,
            action.destructive ? "Destructive action. Tap to run." : "Tap to run."));

        TextView title = new TextView(activity);
        title.setText(action.label);
        title.setTextColor(action.destructive ? GHOSTEX_DANGER : GHOSTEX_TEXT);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(withBottomMargin(title, dp(4)));

        TextView body = bodyText(action.detail);
        body.setTextSize(12);
        row.addView(body);
        return row;
    }

    private View detailRow(@NonNull GhostexDetail detail) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, 0);

        TextView label = new TextView(activity);
        label.setText(detail.label);
        label.setTextColor(GHOSTEX_MUTED);
        label.setTextSize(12);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(withBottomMargin(label, dp(2)));

        TextView value = new TextView(activity);
        value.setText(detail.value);
        value.setTextColor(GHOSTEX_TEXT);
        value.setTextSize(14);
        value.setLineSpacing(0, 1.08f);
        row.addView(value);
        return row;
    }

    private void resetKnownHost(@NonNull GhostexMachine machine) {
        if (!ensureMachineStillExists(machine, "resetting its SSH host key")) return;
        machineStore.setLastMachineId(machine.id);
        refreshMachineControls();
        setStatus("Resetting SSH host key for " + machine.displayLabel() + "...");
        executor.execute(() -> {
            GhostexSshTransport.CommandResult status =
                new GhostexSshTransport(activity).resetPersistedHostKey(machine);
            mainHandler.post(() -> {
                if (!isCurrentMachine(machine) || !machineMatchesSavedTarget(machine)) return;
                setStatus(status.output);
                if (status.exitCode == 0) reconnectLastMachine(false);
            });
        });
    }

    private void resetKnownHostForCheck(@NonNull GhostexMachine machine) {
        if (!ensureMachineStillExists(machine, "resetting its SSH host key")) return;
        setStatus("Resetting SSH host key for " + machine.displayLabel() + "...");
        executor.execute(() -> {
            GhostexSshTransport.CommandResult status =
                new GhostexSshTransport(activity).resetPersistedHostKey(machine);
            mainHandler.post(() -> {
                if (!machineMatchesSavedTarget(machine)) return;
                setStatus(status.output);
                if (status.exitCode == 0) checkMachineConnection(machine);
            });
        });
    }

    private void showMachineEditor(@Nullable GhostexMachine existingMachine) {
        showMachineEditor(existingMachine, false);
    }

    private void showMachineEditor(@Nullable GhostexMachine existingMachine,
                                   boolean requiredFirstRunHandoff) {
        requiredMachineSetupInProgress = requiredFirstRunHandoff;
        LinearLayout form = verticalContainer(dp(18));
        form.setBackgroundColor(GHOSTEX_BG);

        addTitle(form, existingMachine == null ? "Add a Mac or workstation" : "Edit SSH machine", 18);
        addBody(form, "Ghostex Android connects over Tailscale SSH, then runs the Ghostex CLI on this machine to list and attach ZMX sessions.");

        EditText name = input("Display name", existingMachine == null ? "" : existingMachine.name, InputType.TYPE_CLASS_TEXT);
        EditText host = input("Tailscale host or IP", existingMachine == null ? "" : existingMachine.host, InputType.TYPE_CLASS_TEXT);
        EditText username = input("SSH username", existingMachine == null ? "" : existingMachine.username, InputType.TYPE_CLASS_TEXT);
        EditText port = input("SSH port", existingMachine == null ? "22" : String.valueOf(existingMachine.port), InputType.TYPE_CLASS_NUMBER);
        EditText password = input("SSH password", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox savePassword = new CheckBox(activity);
        savePassword.setText(R.string.ghostex_save_password_securely);
        savePassword.setChecked(existingMachine != null && existingMachine.savePassword && passwordVault.isPasswordSavingSupported());
        savePassword.setEnabled(passwordVault.isPasswordSavingSupported());
        if (!passwordVault.isPasswordSavingSupported()) {
            savePassword.setText(R.string.ghostex_password_saving_requires_android_m);
        }
        password.setHint(existingMachine != null && passwordVault.hasPassword(existingMachine.id)
            ? "SSH password (leave blank to keep saved password)"
            : "SSH password (optional)");
        password.setEnabled(true);
        styleCheckBox(savePassword);

        LinearLayout connectionCard = card();
        addTitle(connectionCard, "Connection", 15);
        connectionCard.addView(withBottomMargin(name, dp(8)));
        connectionCard.addView(withBottomMargin(host, dp(8)));
        connectionCard.addView(withBottomMargin(username, dp(8)));
        connectionCard.addView(port);
        form.addView(withBottomMargin(connectionCard, dp(12)));

        LinearLayout passwordCard = card();
        addTitle(passwordCard, "Password", 15);
        addBody(passwordCard, "Leave the password empty to use SSH keys or Tailscale SSH. If entered without saving, it stays only in memory for this app run.");
        passwordCard.addView(withBottomMargin(savePassword, dp(8)));
        passwordCard.addView(password);
        form.addView(passwordCard);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Save", null)
            .create();
        dialog.setOnDismissListener(ignored -> {
            if (requiredFirstRunHandoff) requiredMachineSetupInProgress = false;
        });
        GhostexDialogStyler.show(dialog, () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (saveMachine(existingMachine, name, host, username, port, savePassword, password)) {
                if (requiredFirstRunHandoff) requiredMachineSetupInProgress = false;
                dialog.dismiss();
            }
        }));
    }

    private boolean saveMachine(@Nullable GhostexMachine existingMachine, @NonNull EditText name,
                             @NonNull EditText host, @NonNull EditText username,
                             @NonNull EditText port, @NonNull CheckBox savePassword,
                             @NonNull EditText password) {
        clearErrors(name, host, username, port, password);
        if (existingMachine != null && !ensureMachineStillExists(existingMachine, "editing")) {
            host.setError("This machine was removed. Add it again before editing.");
            return false;
        }
        String cleanHost = host.getText().toString().trim();
        String cleanUsername = username.getText().toString().trim();
        boolean valid = true;
        String hostError = GhostexMachineValidation.hostError(cleanHost);
        if (hostError != null) {
            host.setError(hostError);
            valid = false;
        }
        String usernameError = GhostexMachineValidation.usernameError(cleanUsername);
        if (usernameError != null) {
            username.setError(usernameError);
            valid = false;
        }
        int cleanPort = parsePort(port.getText().toString());
        if (cleanPort == -1) {
            port.setError("Use a port from 1 to 65535.");
            valid = false;
        }
        if (valid) {
            /*
            CDXC:AndroidConnectionManagement 2026-05-17-21:07:
            Machine setup should prevent accidental duplicate reconnect targets
            before persistence. Multiple accounts remain supported because the
            check includes SSH username and port, but identical targets would
            make auto-reconnect and the drawer switcher ambiguous.
            */
            String duplicateMachineId = machineStore.findDuplicateSshTargetId(cleanHost, cleanUsername,
                cleanPort, existingMachine == null ? null : existingMachine.id);
            if (duplicateMachineId != null) {
                host.setError("This SSH target is already saved.");
                username.setError("Use Connect on the existing machine, or change the username, host, or port.");
                valid = false;
            }
        }
        boolean targetChanged = existingMachine != null &&
            !existingMachine.hasSameSshTarget(cleanHost, cleanUsername, cleanPort);
        boolean existingPasswordSaved = existingMachine != null && !targetChanged &&
            passwordVault.hasPassword(existingMachine.id);
        if (savePassword.isChecked() && password.getText().toString().isEmpty() && !existingPasswordSaved) {
            password.setError(targetChanged
                ? "Enter a password for this SSH target or uncheck Save password."
                : "Enter a password or uncheck Save password.");
            valid = false;
        }
        if (!valid) {
            setStatus("Fix the highlighted machine details.");
            return false;
        }
        GhostexMachine machine = existingMachine == null
            ? GhostexMachine.create(name.getText().toString().trim(), cleanHost, cleanUsername, cleanPort, savePassword.isChecked())
            : new GhostexMachine(existingMachine.id, name.getText().toString().trim(), cleanHost, cleanUsername, cleanPort,
                savePassword.isChecked(), existingMachine.lastConnectedAt);

        try {
            if (machine.savePassword && password.getText().length() > 0) {
                passwordVault.savePassword(machine.id, password.getText().toString());
                sessionPasswords.remove(machine.id);
            } else if (!machine.savePassword) {
                passwordVault.deletePassword(machine.id);
                if (password.getText().length() > 0) {
                    sessionPasswords.put(machine.id, password.getText().toString());
                } else {
                    sessionPasswords.remove(machine.id);
                }
            }
        } catch (Exception error) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save SSH password", error);
            password.setError("Could not store this password securely.");
            setStatus("Password was not saved. Check Android secure storage and try again.");
            return false;
        }

        /*
         * CDXC:AndroidConnectionManagement 2026-05-17-14:12:
         * Settings is a multi-machine manager. Adding a machine or editing the selected SSH target should connect immediately, but editing another saved machine must not surprise-switch the active remote account.
         */
        boolean shouldSelectAfterSave = existingMachine == null ||
            existingMachine.id.equals(machineStore.getLastMachineId());
        if (existingMachine != null &&
            GhostexMachineManagementPolicy.shouldEvictWarmSessionsAfterTargetEdit(targetChanged)) {
            /*
            CDXC:AndroidRemoteSessions 2026-05-17-15:19:
            The saved machine id survives host/user/port edits so settings,
            credentials, and selection state remain stable. Warm terminal
            surfaces do not survive that edit because they are live SSH
            sessions to the old target behind the same id.
            */
            finishWarmSessionsForMachine(machine.id);
            sessionAdapter.clearActiveSessionForMachine(machine.id);
        }
        if (existingMachine != null &&
            GhostexMachineManagementPolicy.shouldInvalidateRequestsAfterTargetEdit(targetChanged)) {
            /*
            CDXC:AndroidConnectionManagement 2026-05-17-15:21:
            Target edits keep the machine id stable, but any in-flight check,
            reconnect, attach preflight, or remote action was started against
            the old host/user/port. Advance generations before saving the new
            target so old callbacks cannot report success, prompt for
            credentials, or update the drawer for the edited account.
            */
            machineCheckGeneration++;
            if (machine.id.equals(machineStore.getLastMachineId())) {
                reconnectGeneration++;
                remoteActionGeneration++;
                attachGeneration++;
            }
        }
        machineStore.saveMachine(machine);
        if (GhostexOnboardingCompletionPolicy.shouldMarkTutorialSeenAfterMachineSave(
            machineStore.hasSeenTutorial(), !machineStore.getMachines().isEmpty())) {
            /*
            CDXC:AndroidOnboarding 2026-05-17-19:11:
            A fresh install should not forget the required tutorial just because
            the user tapped Add machine and canceled. Complete first-run
            onboarding only after the first SSH machine is actually persisted.
            */
            machineStore.setTutorialSeen(true);
        }
        refreshMachineControls();
        if (shouldSelectAfterSave) {
            machineStore.setLastMachineId(machine.id);
            reconnectLastMachine(false);
        } else {
            setStatus("Saved " + machine.displayLabel() + ". Use Connect when you want to switch to it.");
        }
        return true;
    }

    private void clearErrors(@NonNull EditText... fields) {
        for (EditText field : fields) field.setError(null);
    }

    private EditText input(@NonNull String hint, @NonNull String value, int inputType) {
        EditText editText = new EditText(activity);
        editText.setHint(hint);
        editText.setText(value);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        editText.setTextColor(GHOSTEX_TEXT);
        editText.setHintTextColor(GHOSTEX_MUTED);
        editText.setTextSize(15);
        editText.setPadding(dp(12), 0, dp(12), 0);
        editText.setMinHeight(dp(48));
        editText.setBackground(panelBackground(GHOSTEX_PANEL_ALT));
        editText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return editText;
    }

    private void openTailscale() {
        if (!GhostexTailscaleLauncher.launch(activity)) {
            setStatus("Could not open Tailscale. Install it from tailscale.com/download, then retry connection.");
        }
    }

    @Nullable
    private String readPassword(@NonNull GhostexMachine machine) {
        String sessionPassword = sessionPasswords.get(machine.id);
        if (sessionPassword != null && !sessionPassword.isEmpty()) return sessionPassword;
        if (!machine.savePassword) return null;
        try {
            return passwordVault.readPassword(machine.id);
        } catch (Exception error) {
            /*
            CDXC:AndroidConnectionSecurity 2026-05-17-19:59:
            If Android Keystore cannot read a saved SSH password, remove the
            saved-password mode from the current machine record. The next
            reconnect should prompt for credentials instead of repeatedly
            claiming a saved password exists.
            */
            GhostexMachine currentMachine = currentMatchingMachine(machine);
            if (currentMachine != null) {
                sessionPasswords.remove(machine.id);
                machineStore.saveMachine(currentMachine.withSavePassword(false));
            }
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read SSH password", error);
            return null;
        }
    }

    private void maybePromptForPasswordAfterFailure(@NonNull GhostexMachine machine, @Nullable String errorMessage) {
        maybePromptForPasswordAfterFailure(machine, errorMessage, "Connect", () -> {
            machineStore.setLastMachineId(machine.id);
            refreshMachineControls();
            reconnectLastMachine(false);
        });
    }

    private void maybePromptForPasswordAfterFailure(@NonNull GhostexMachine machine,
                                                    @Nullable String errorMessage,
                                                    @NonNull String primaryLabel,
                                                    @NonNull Runnable afterCredentialAccepted) {
        if (GhostexConnectionRecovery.shouldPromptForPassword(errorMessage)) {
            showPasswordPrompt(machine, errorMessage == null ? "" : errorMessage,
                primaryLabel, afterCredentialAccepted);
        }
    }

    private void maybePromptForPasswordAfterCheckFailure(@NonNull GhostexMachine machine, @Nullable String errorMessage) {
        if (GhostexConnectionRecovery.shouldPromptForPassword(errorMessage)) {
            showPasswordPrompt(machine, errorMessage == null ? "" : errorMessage,
                "Check", () -> checkMachineConnection(machine));
        }
    }

    private void maybePromptForHostKeyResetAfterCheckFailure(@NonNull GhostexMachine machine, @Nullable String errorMessage) {
        if (GhostexConnectionRecovery.shouldPromptForHostKeyReset(errorMessage)) {
            confirmResetKnownHostForCheck(machine);
        }
    }

    private void showPasswordPrompt(@NonNull GhostexMachine machine, @NonNull String message) {
        showPasswordPrompt(machine, message, "Connect", () -> {
            machineStore.setLastMachineId(machine.id);
            refreshMachineControls();
            reconnectLastMachine(false);
        });
    }

    private void showMachinePasswordManager(@NonNull GhostexMachine machine) {
        /*
        CDXC:AndroidConnectionManagement 2026-05-17-14:37:
        Settings is for managing multiple SSH machines. Saving or updating a
        credential from Settings must not switch the active account; the user
        uses Connect when they want to make this machine the reconnect target.
        */
        showPasswordPrompt(machine,
            "Save or hold a password for this machine. Use Connect when you want Ghostex Android to switch to it.",
            "Save",
            () -> {
                refreshMachineControls();
                if (GhostexMachineManagementPolicy.shouldReconnectAfterSettingsPasswordSave()) {
                    reconnectLastMachine(false);
                    return;
                }
                setStatus("Saved password settings for " + machine.displayLabel() + ". Use Connect to switch machines.");
            });
    }

    private void showPasswordPrompt(@NonNull GhostexMachine machine, @NonNull String message,
                                    @NonNull String primaryLabel,
                                    @NonNull Runnable afterCredentialAccepted) {
        LinearLayout form = verticalContainer(dp(18));
        form.setBackgroundColor(GHOSTEX_BG);
        addTitle(form, machine.displayLabel(), 18);
        addBody(form, message);

        EditText password = input("SSH password", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox savePassword = new CheckBox(activity);
        savePassword.setText(R.string.ghostex_save_password_securely);
        savePassword.setChecked(machine.savePassword && passwordVault.isPasswordSavingSupported());
        savePassword.setEnabled(passwordVault.isPasswordSavingSupported());
        if (!passwordVault.isPasswordSavingSupported()) {
            savePassword.setText(R.string.ghostex_password_saving_requires_android_m);
        }
        styleCheckBox(savePassword);

        LinearLayout card = card();
        addTitle(card, "SSH password", 15);
        addBody(card, "Saved passwords use Android Keystore. Unchecked passwords are used only until Ghostex Android is closed.");
        card.addView(withBottomMargin(password, dp(8)));
        card.addView(savePassword);
        form.addView(card);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(primaryLabel, null)
            .create();
        GhostexDialogStyler.show(dialog, () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            password.setError(null);
            /*
            CDXC:AndroidConnectionManagement 2026-05-17-14:40:
            A stale password dialog must not recreate a saved machine that was
            deleted while the dialog was open. Treat the prompt as expired and
            require the user to add the machine again if they still need it.
            */
            if (!GhostexMachineManagementPolicy.canAcceptCredentialPromptForExistingMachine(
                machineMatchesSavedTarget(machine))) {
                password.setError("This machine changed or was removed. Open the current machine before saving a password.");
                setStatus("That SSH machine changed or was removed. Open the current machine before saving credentials.");
                return;
            }
            GhostexMachine currentMachine = currentMatchingMachine(machine);
            if (currentMachine == null) return;
            String value = password.getText().toString();
            if (value.isEmpty()) {
                password.setError("Enter the SSH password.");
                return;
            }
            if (savePassword.isChecked()) {
                try {
                    passwordVault.savePassword(machine.id, value);
                    sessionPasswords.remove(machine.id);
                    /*
                    CDXC:AndroidConnectionManagement 2026-05-17-15:25:
                    Save only the credential mode on top of the latest saved
                    machine metadata. The prompt's opener may have an older
                    display name or Last Connected timestamp even when its SSH
                    target is still valid.
                    */
                    machineStore.saveMachine(currentMachine.withSavePassword(true));
                } catch (Exception error) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save SSH password", error);
                    password.setError("Could not store this password securely.");
                    setStatus("Password was not saved. Check Android secure storage and try again.");
                    return;
                }
            } else {
                /*
                CDXC:AndroidConnectionSecurity 2026-05-17-12:33:
                When recovery uses an unchecked password, treat it as a true
                session-only credential. Clear any older saved password and
                saved-password flag so the next cold start does not retry a
                stale rejected secret after the user explicitly declined saving.
                */
                passwordVault.deletePassword(machine.id);
                sessionPasswords.put(machine.id, value);
                machineStore.saveMachine(currentMachine.withSavePassword(false));
            }
            afterCredentialAccepted.run();
            dialog.dismiss();
        }));
    }

    private LinearLayout verticalContainer(int padding) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);
        layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private LinearLayout card() {
        LinearLayout layout = verticalContainer(dp(14));
        layout.setBackground(panelBackground(GHOSTEX_PANEL));
        return layout;
    }

    private LinearLayout horizontalActions() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private void addTwoColumnActionRows(@NonNull LinearLayout container,
                                        @NonNull View... actions) {
        for (int i = 0; i < actions.length; i += 2) {
            LinearLayout row = horizontalActions();
            row.addView(actions[i]);
            if (i + 1 < actions.length) {
                row.addView(actions[i + 1]);
            } else {
                TextView spacer = new TextView(activity);
                spacer.setVisibility(View.INVISIBLE);
                row.addView(spacer, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            View wrappedRow = i == 0 ? withTopMargin(row, dp(8)) : withTopMargin(row, dp(6));
            container.addView(wrappedRow);
        }
    }

    private TextView actionPill(@NonNull String label, @NonNull View.OnClickListener listener) {
        TextView view = new TextView(activity);
        /*
        CDXC:AndroidSidebar 2026-05-17-21:22:
        Machine/settings/setup panels use custom text rows instead of native
        buttons so labels can wrap cleanly in two columns. Keep those controls
        at a 48dp minimum height so the polished panel layout still preserves
        Android's normal touch-target affordance.
        */
        view.setText(label);
        view.setContentDescription(GhostexAccessibilityCopy.join(label, "Tap to run."));
        view.setTextColor(GHOSTEX_TEXT);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setMaxLines(2);
        view.setSingleLine(false);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(panelBackground(GHOSTEX_PANEL_ALT));
        view.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        view.setLayoutParams(params);
        return view;
    }

    private void styleCheckBox(@NonNull CheckBox checkBox) {
        checkBox.setTextColor(GHOSTEX_TEXT);
        checkBox.setTextSize(14);
        checkBox.setMinHeight(dp(48));
        checkBox.setPadding(0, 0, 0, 0);
    }

    private void addStepCard(@NonNull LinearLayout container, int number, @NonNull String body) {
        LinearLayout card = card();
        addTitle(card, number + ". " + body, 15);
        container.addView(withBottomMargin(card, dp(10)));
    }

    private void addTitle(@NonNull LinearLayout container, @NonNull String text, int sp) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(GHOSTEX_TEXT);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setLineSpacing(0, 1.08f);
        GhostexTextSemantics.markHeading(view);
        container.addView(withBottomMargin(view, dp(6)));
    }

    private void addBody(@NonNull LinearLayout container, @NonNull String text) {
        container.addView(withBottomMargin(bodyText(text), dp(8)));
    }

    private TextView bodyText(@NonNull String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(GHOSTEX_MUTED);
        view.setTextSize(14);
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private View withBottomMargin(@NonNull View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, margin);
        view.setLayoutParams(params);
        return view;
    }

    private View withTopMargin(@NonNull View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, margin, 0, 0);
        view.setLayoutParams(params);
        return view;
    }

    private GradientDrawable panelBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), GHOSTEX_BORDER);
        return drawable;
    }

    private int parsePort(@NonNull String value) {
        return GhostexMachineValidation.parsePort(value);
    }

    private void setStatus(@NonNull String text) {
        if (destroyed) return;
        if (statusView != null) statusView.setText(text);
        if (machinesPageStatusView != null) machinesPageStatusView.setText(text);
    }

    private String blankToDash(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private interface SettingCheckedListener {
        void onCheckedChanged(boolean checked);
    }

    private interface SettingSelectedListener {
        void onSelected(@NonNull String value);
    }

    private interface SettingsMutation {
        void run() throws Exception;
    }

    private static final class GhostexAction {
        final String label;
        final String detail;
        final boolean destructive;
        final Runnable runnable;

        GhostexAction(@NonNull String label, @NonNull String detail, boolean destructive,
                      @NonNull Runnable runnable) {
            this.label = label;
            this.detail = detail;
            this.destructive = destructive;
            this.runnable = runnable;
        }

        void run() {
            runnable.run();
        }
    }

    private static final class CachedAttachment {
        final File file;
        final String remotePath;
        final boolean image;

        CachedAttachment(@NonNull File file, @NonNull String remotePath, boolean image) {
            this.file = file;
            this.remotePath = remotePath;
            this.image = image;
        }
    }

    private static final class GhostexDetail {
        final String label;
        final String value;

        GhostexDetail(@NonNull String label, @NonNull String value) {
            this.label = label;
            this.value = value;
        }
    }

}
