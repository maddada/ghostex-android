package com.termux.app.ghostex;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.List;

public final class GhostexRemoteSessionAdapter extends ArrayAdapter<GhostexDrawerItem> {

    private static final int VIEW_TYPE_PROJECT_HEADER = 0;
    private static final int VIEW_TYPE_SESSION = 1;
    private static final int VIEW_TYPE_STATE_CARD = 2;
    private String currentMachineId;
    private String activeSessionKey;
    private OnProjectSessionCreateListener projectSessionCreateListener;
    private OnProjectActionsListener projectActionsListener;
    private OnProjectToggleListener projectToggleListener;

    public interface OnProjectSessionCreateListener {
        void onCreateProjectSession(@NonNull GhostexDrawerItem item);
    }

    public interface OnProjectActionsListener {
        void onOpenProjectActions(@NonNull GhostexDrawerItem item);
    }

    public interface OnProjectToggleListener {
        void onToggleProject(@NonNull GhostexDrawerItem item);
    }

    /*
    CDXC:AndroidSidebar 2026-05-17-10:13:
    The Android drawer renders Ghostex sessions as compact cards over the
    terminal surface. Keep the visual hierarchy close to the macOS sidebar:
    alias badge, readable title, project/status/provider metadata, and
    status color as a fast scanning cue.

    CDXC:AndroidSidebar 2026-05-17-10:43:
    The adapter now renders both project headers and session rows so Android
    carries the same grouped sidebar hierarchy and row action targets as the
    macOS sidebar instead of flattening every ZMX session.

    CDXC:AndroidConnectionManagement 2026-05-17-13:02:
    Drawer state cards turn reconnect, setup, no-session, and failure states
    into first-class visible rows. This keeps recovery instructions inside the
    overlay area where users already switch machines and reach Retry/Setup.

    CDXC:AndroidSidebar 2026-05-17-20:27:
    Active session styling is machine-scoped. Users can save multiple Macs
    whose Ghostex CLI returns the same session id, so highlighting by session id
    alone would mark the wrong machine's row as active after account switches.

    CDXC:AndroidConnectionRecovery 2026-05-17-12:35:
    Drawer state cards are recovery surfaces, not decorative copy. Keep them
    enabled so tapping the setup, failure, or empty-session card can open the
    same Ghostex-styled repair actions as the surrounding controls.

    CDXC:AndroidSidebar 2026-05-17-12:45:
    Include Last Active copy in Android session-card metadata because the
    macOS sidebar uses recency as a primary switching cue. The row keeps the
    text ellipsized so project, recency, provider, and agent remain compact on
    narrow Android drawers.

    CDXC:AndroidSidebar 2026-05-17-18:38:
    Drawer rows are clickable and long-pressable controls, not decorative text.
    Give state cards, project headers, and session cards composed accessibility
    descriptions so the remote-session overlay remains usable when visual
    sidebar scanning is not available.

    CDXC:AndroidSidebar 2026-05-17-19:42:
    Android session rows should borrow the macOS sidebar palette and card shape
    instead of using flat ListView row fills. Keep this styling in the copied
    Ghostex adapter so upstream Termux drawer code remains easy to sync.

    CDXC:AndroidSidebar 2026-05-17-19:54:
    Consume the shared GhostexPalette instead of duplicating drawer colors in
    every widget helper. The palette itself owns the JVM-safe literal ARGB
    contract for unit tests and release gates.

    CDXC:AndroidSidebar 2026-05-18-02:31:
    Project headers need a touch-first create-session affordance beside the
    session count. The button must route through the Mac Ghostex CLI so the
    main app creates the terminal and applies its current zmx persistence
    setting instead of Android opening a local Termux session.

    CDXC:AndroidSidebar 2026-05-18-05:18:
    Project actions should not depend on long-press discovery now that the
    project header has an inline New session button. Keep a three-dot button to
    the right of `+` that opens the same project action sheet directly.

    CDXC:AndroidSidebar 2026-05-18-06:51:
    The project action affordance should render as a real vertical-more icon,
    not the literal text `...`, so the header reads as native mobile controls
    beside the existing plus button.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    The project title, session-count pill, and plus button should share an exact
    32dp header height so they read as one control row. Keep the overflow button
    the same touch height but shrink its icon with padding so project actions are
    secondary to title, count, and session creation.

    CDXC:AndroidSidebar 2026-05-18-16:13:
    Tapping the project header toggles expanded/collapsed state. Inline plus and
    vertical-more buttons remain separate click targets, so project disclosure
    does not steal create-session or overflow actions.

    CDXC:AndroidSidebar 2026-05-19-10:15:
    Android session cards stay single-line title rows without the alias badge,
    metadata line, or status pill so the drawer matches the simplified macOS
    sidebar density on phones.

    CDXC:AndroidSidebar 2026-05-19-10:15:
    Project headers should not show a session-count pill. Keep project title plus
    create and overflow actions only.

    CDXC:AndroidSidebar 2026-05-19-11:05:
    Session cards show the agent logo on the left, matching macOS sidebar session
    buttons. Use the shared Ghostex agentIcon field when present and fall back to
    the terminal glyph for plain terminal sessions.
    */
    public GhostexRemoteSessionAdapter(@NonNull Context context,
                                       @NonNull List<GhostexDrawerItem> items) {
        super(context, 0, items);
    }

    public void setOnProjectSessionCreateListener(@Nullable OnProjectSessionCreateListener listener) {
        projectSessionCreateListener = listener;
    }

    public void setOnProjectActionsListener(@Nullable OnProjectActionsListener listener) {
        projectActionsListener = listener;
    }

    public void setOnProjectToggleListener(@Nullable OnProjectToggleListener listener) {
        projectToggleListener = listener;
    }

    public void setCurrentMachineId(@Nullable String value) {
        currentMachineId = value;
        notifyDataSetChanged();
    }

    public void setActiveSessionKey(@Nullable String value) {
        activeSessionKey = value;
        notifyDataSetChanged();
    }

    public void clearActiveSessionForMachine(@NonNull String machineId) {
        if (activeSessionKey != null && GhostexWarmSessionKey.belongsToMachine(activeSessionKey, machineId)) {
            activeSessionKey = null;
            notifyDataSetChanged();
        }
    }

    public static boolean isActiveSession(@Nullable String currentMachineId,
                                          @Nullable String activeSessionKey,
                                          @NonNull GhostexRemoteSession session) {
        return currentMachineId != null &&
            activeSessionKey != null &&
            activeSessionKey.equals(GhostexWarmSessionKey.forIds(currentMachineId, session.sessionId));
    }

    public void setActiveSession(@NonNull GhostexMachine machine,
                                 @NonNull GhostexRemoteSession session) {
        currentMachineId = machine.id;
        activeSessionKey = GhostexWarmSessionKey.forSession(machine, session);
        notifyDataSetChanged();
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        GhostexDrawerItem item = getItem(position);
        if (item != null && item.type == GhostexDrawerItem.Type.STATE_CARD) return VIEW_TYPE_STATE_CARD;
        return item != null && item.type == GhostexDrawerItem.Type.PROJECT_HEADER
            ? VIEW_TYPE_PROJECT_HEADER
            : VIEW_TYPE_SESSION;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        GhostexDrawerItem item = getItem(position);
        if (item != null && item.type == GhostexDrawerItem.Type.STATE_CARD) {
            return getStateCardView(item, convertView, parent);
        }
        if (item != null && item.type == GhostexDrawerItem.Type.PROJECT_HEADER) {
            return getProjectHeaderView(item, convertView, parent);
        }
        return getSessionView(item, convertView, parent);
    }

    private View getStateCardView(@NonNull GhostexDrawerItem item, View convertView,
                                  @NonNull ViewGroup parent) {
        LinearLayout row = convertView instanceof LinearLayout && "state".equals(convertView.getTag())
            ? (LinearLayout) convertView
            : createStateCard(parent);
        TextView title = (TextView) row.findViewWithTag("stateTitle");
        TextView body = (TextView) row.findViewWithTag("stateBody");
        TextView action = (TextView) row.findViewWithTag("stateAction");
        title.setText(item.stateTitle);
        body.setText(item.stateBody);
        action.setText(item.stateActionHint);
        action.setVisibility(item.stateActionHint.isEmpty() ? View.GONE : View.VISIBLE);
        row.setContentDescription(GhostexAccessibilityCopy.join(item.stateTitle, item.stateBody,
            item.stateActionHint.isEmpty() ? "Tap for recovery actions." : item.stateActionHint));
        return row;
    }

    private View getProjectHeaderView(@NonNull GhostexDrawerItem item, View convertView,
                                      @NonNull ViewGroup parent) {
        LinearLayout row = convertView instanceof LinearLayout && "project".equals(convertView.getTag())
            ? (LinearLayout) convertView
            : createProjectHeader(parent);
        TextView title = (TextView) row.findViewWithTag("projectTitle");
        TextView create = (TextView) row.findViewWithTag("projectCreate");
        ImageButton actions = (ImageButton) row.findViewWithTag("projectActions");
        title.setText(item.projectTitle);
        row.setOnClickListener(view -> {
            if (projectToggleListener != null) {
                projectToggleListener.onToggleProject(item);
            }
        });
        create.setContentDescription("Create a session in " + item.projectTitle);
        create.setOnClickListener(view -> {
            if (projectSessionCreateListener != null) {
                projectSessionCreateListener.onCreateProjectSession(item);
            }
        });
        actions.setContentDescription("Open project actions for " + item.projectTitle);
        actions.setOnClickListener(view -> {
            if (projectActionsListener != null) {
                projectActionsListener.onOpenProjectActions(item);
            }
        });
        row.setContentDescription(GhostexAccessibilityCopy.join(item.projectTitle,
            item.collapsed ? "Tap to expand. Use plus to create a session. Use more for project actions."
                : "Tap to collapse. Use plus to create a session. Use more for project actions."));
        return row;
    }

    private View getSessionView(@Nullable GhostexDrawerItem item, View convertView,
                                @NonNull ViewGroup parent) {
        LinearLayout row = convertView instanceof LinearLayout && "session".equals(convertView.getTag())
            ? (LinearLayout) convertView
            : createSessionRow(parent);
        GhostexRemoteSession session = item == null ? null : item.session;
        TextView title = (TextView) row.findViewWithTag("title");
        ImageView agentIcon = (ImageView) row.findViewWithTag("agentIcon");
        if (session == null) return row;

        title.setText(session.title.isEmpty() ? "Ghostex Session" : session.title);
        agentIcon.setImageResource(GhostexSessionAgentIcon.drawableResForSession(session));
        agentIcon.setColorFilter(GhostexSessionAgentIcon.tintColorForSession(session));
        row.setBackground(sessionBackground(parent.getContext(),
            isActiveSession(currentMachineId, activeSessionKey, session)));
        row.setContentDescription(GhostexAccessibilityCopy.join(title.getText().toString(),
            "Tap to attach. Long press for actions."));
        return row;
    }

    private LinearLayout createProjectHeader(@NonNull ViewGroup parent) {
        /*
        CDXC:AndroidSidebar 2026-05-18-05:07:
        The per-project New session action lives in the sessions list header.
        Keep the plus immediately to the right of the sessions-count pill and
        size it from the same text/padding recipe so it does not overpower the
        pill on narrow Android drawers.

        CDXC:AndroidSidebar 2026-05-18-05:18:
        The project action sheet needs a visible overflow affordance because
        the inline plus button made long-pressing the project name unreliable
        and undiscoverable. Place the three-dot button to the right of plus so
        project creation and project management are adjacent but distinct.

        CDXC:AndroidSidebar 2026-05-18-06:51:
        Render the overflow affordance with a vector vertical-dots icon instead
        of text so it remains recognizable as an Android action button at small
        header sizes.
        */
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setTag("project");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 6), dp(context, 14), dp(context, 6), dp(context, 6));
        row.setMinimumHeight(dp(context, 52));

        TextView title = new TextView(context);
        title.setTag("projectTitle");
        title.setTextColor(GhostexPalette.FOREGROUND);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinHeight(dp(context, 32));
        row.addView(title, new LinearLayout.LayoutParams(0, dp(context, 32), 1));

        TextView create = new TextView(context);
        create.setTag("projectCreate");
        create.setText("+");
        create.setTextColor(GhostexPalette.FOREGROUND);
        create.setTextSize(15);
        create.setTypeface(Typeface.DEFAULT_BOLD);
        create.setGravity(Gravity.CENTER);
        create.setMinHeight(dp(context, 32));
        create.setPadding(0, 0, 0, dp(context, 1));
        create.setBackground(pillBackground(context));
        create.setClickable(true);
        create.setFocusable(true);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32));
        createParams.setMarginStart(dp(context, 6));
        row.addView(create, createParams);

        ImageButton actions = new ImageButton(context);
        actions.setTag("projectActions");
        actions.setImageResource(R.drawable.ic_ghostex_more_vertical);
        actions.setColorFilter(GhostexPalette.FOREGROUND);
        actions.setScaleType(ImageView.ScaleType.CENTER);
        actions.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        actions.setBackground(pillBackground(context));
        actions.setClickable(true);
        actions.setFocusable(true);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32));
        actionsParams.setMarginStart(dp(context, 6));
        row.addView(actions, actionsParams);
        return row;
    }

    private LinearLayout createStateCard(@NonNull ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setTag("state");
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        row.setMinimumHeight(dp(context, 104));
        row.setBackground(panelBackground(context, GhostexPalette.CARD_ACTIVE));

        TextView title = new TextView(context);
        title.setTag("stateTitle");
        title.setTextColor(GhostexPalette.FOREGROUND);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(0, 1.08f);
        row.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(context);
        body.setTag("stateBody");
        body.setTextColor(GhostexPalette.MUTED);
        body.setTextSize(12);
        body.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.setMargins(0, dp(context, 6), 0, 0);
        row.addView(body, bodyParams);

        TextView action = new TextView(context);
        action.setTag("stateAction");
        action.setTextColor(GhostexPalette.BUTTON);
        action.setTextSize(12);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(0, dp(context, 10), 0, 0);
        row.addView(action, actionParams);
        return row;
    }

    private GradientDrawable panelBackground(@NonNull Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setStroke(dp(context, 1), GhostexPalette.BORDER);
        return drawable;
    }

    private GradientDrawable sessionBackground(@NonNull Context context, boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(active ? GhostexPalette.CARD_ACTIVE : GhostexPalette.CARD);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setStroke(dp(context, 1), active ? GhostexPalette.BUTTON : GhostexPalette.BORDER);
        return drawable;
    }

    private GradientDrawable pillBackground(@NonNull Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(GhostexPalette.BACKGROUND);
        drawable.setCornerRadius(dp(context, 999));
        drawable.setStroke(dp(context, 1), GhostexPalette.BORDER);
        return drawable;
    }

    private LinearLayout createSessionRow(@NonNull ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setTag("session");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 10), dp(context, 9), dp(context, 10), dp(context, 9));
        row.setMinimumHeight(dp(context, 44));

        ImageView agentIcon = new ImageView(context);
        agentIcon.setTag("agentIcon");
        agentIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        agentIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams =
            new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18));
        iconParams.setMarginEnd(dp(context, 10));
        row.addView(agentIcon, iconParams);

        TextView title = new TextView(context);
        title.setTag("title");
        title.setTextColor(GhostexPalette.FOREGROUND);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    static int statusColor(@NonNull GhostexRemoteSession session) {
        if (session.isFocused) return GhostexPalette.BUTTON;
        String status = session.displayStatus();
        if ("working".equals(status)) return GhostexPalette.STATUS_WORKING;
        if ("attention".equals(status)) return GhostexPalette.STATUS_ATTENTION;
        if ("sleep".equals(status) || "sleeping".equals(status)) return GhostexPalette.STATUS_SLEEPING;
        return GhostexPalette.MUTED;
    }

    private int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

}
