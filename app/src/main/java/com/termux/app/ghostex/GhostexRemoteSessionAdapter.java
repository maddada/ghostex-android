package com.termux.app.ghostex;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class GhostexRemoteSessionAdapter extends ArrayAdapter<GhostexDrawerItem> {

    private static final int VIEW_TYPE_PROJECT_HEADER = 0;
    private static final int VIEW_TYPE_SESSION = 1;
    private static final int VIEW_TYPE_STATE_CARD = 2;
    private String currentMachineId;
    private String activeSessionKey;
    private OnProjectSessionCreateListener projectSessionCreateListener;

    public interface OnProjectSessionCreateListener {
        void onCreateProjectSession(@NonNull GhostexDrawerItem item);
    }

    /*
    CDXC:AndroidSidebar 2026-05-17-10:13:
    The Android drawer renders Ghostex sessions as compact cards over the
    terminal surface. Keep the visual hierarchy close to the macOS sidebar:
    alias badge, readable title, project/status/provider metadata, and
    status color as a fast scanning cue.

    CDXC:AndroidSidebar 2026-05-17-10:43:
    The adapter now renders both project headers and session rows so Android
    carries the same grouped sidebar hierarchy and long-press target types as
    the macOS sidebar instead of flattening every ZMX session.

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
    */
    public GhostexRemoteSessionAdapter(@NonNull Context context,
                                       @NonNull List<GhostexDrawerItem> items) {
        super(context, 0, items);
    }

    public void setOnProjectSessionCreateListener(@Nullable OnProjectSessionCreateListener listener) {
        projectSessionCreateListener = listener;
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
        TextView count = (TextView) row.findViewWithTag("projectCount");
        TextView create = (TextView) row.findViewWithTag("projectCreate");
        title.setText(item.projectTitle);
        String summary = buildProjectSummary(item);
        count.setText(summary);
        create.setContentDescription("Create a session in " + item.projectTitle);
        create.setOnClickListener(view -> {
            if (projectSessionCreateListener != null) {
                projectSessionCreateListener.onCreateProjectSession(item);
            }
        });
        row.setContentDescription(GhostexAccessibilityCopy.join(item.projectTitle, summary, "Long press for project actions."));
        return row;
    }

    private View getSessionView(@Nullable GhostexDrawerItem item, View convertView,
                                @NonNull ViewGroup parent) {
        LinearLayout row = convertView instanceof LinearLayout && "session".equals(convertView.getTag())
            ? (LinearLayout) convertView
            : createSessionRow(parent);
        GhostexRemoteSession session = item == null ? null : item.session;
        TextView title = (TextView) row.findViewWithTag("title");
        TextView meta = (TextView) row.findViewWithTag("meta");
        TextView badge = (TextView) row.findViewWithTag("badge");
        TextView statusPill = (TextView) row.findViewWithTag("statusPill");
        if (session == null) return row;

        title.setText(session.title.isEmpty() ? "Ghostex Session" : session.title);
        meta.setText(GhostexSessionCardFormatter.buildMeta(session, System.currentTimeMillis()));
        badge.setText(session.alias);
        int statusColor = statusColor(session);
        badge.setBackground(badgeBackground(parent.getContext(), statusColor));
        String status = statusLabel(session);
        statusPill.setText(status);
        statusPill.setTextColor(statusColor);
        row.setBackground(sessionBackground(parent.getContext(),
            isActiveSession(currentMachineId, activeSessionKey, session)));
        row.setContentDescription(GhostexAccessibilityCopy.join(title.getText().toString(),
            "Session " + session.alias, meta.getText().toString(), status,
            "Tap to attach. Long press for actions."));
        return row;
    }

    private LinearLayout createProjectHeader(@NonNull ViewGroup parent) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setTag("project");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 6), dp(context, 14), dp(context, 6), dp(context, 6));
        row.setMinimumHeight(dp(context, 42));

        TextView title = new TextView(context);
        title.setTag("projectTitle");
        title.setTextColor(GhostexPalette.FOREGROUND);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView create = new TextView(context);
        create.setTag("projectCreate");
        create.setText("+");
        create.setTextColor(GhostexPalette.FOREGROUND);
        create.setTextSize(16);
        create.setTypeface(Typeface.DEFAULT_BOLD);
        create.setGravity(Gravity.CENTER);
        create.setBackground(pillBackground(context));
        create.setClickable(true);
        create.setFocusable(true);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(dp(context, 30), dp(context, 30));
        createParams.setMarginEnd(dp(context, 6));
        row.addView(create, createParams);

        TextView count = new TextView(context);
        count.setTag("projectCount");
        count.setTextColor(GhostexPalette.MUTED);
        count.setTextSize(11);
        count.setGravity(Gravity.CENTER);
        count.setPadding(dp(context, 8), dp(context, 3), dp(context, 8), dp(context, 3));
        count.setBackground(pillBackground(context));
        row.addView(count, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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

    private GradientDrawable badgeBackground(@NonNull Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, 8));
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
        row.setMinimumHeight(dp(context, 68));

        TextView badge = new TextView(context);
        badge.setTag("badge");
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34));
        badgeParams.setMarginEnd(dp(context, 10));
        row.addView(badge, badgeParams);

        LinearLayout textStack = new LinearLayout(context);
        textStack.setOrientation(LinearLayout.VERTICAL);
        textStack.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setTag("title");
        title.setTextColor(GhostexPalette.FOREGROUND);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textStack.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView meta = new TextView(context);
        meta.setTag("meta");
        meta.setTextColor(GhostexPalette.MUTED);
        meta.setTextSize(12);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        metaRow.addView(meta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView statusPill = new TextView(context);
        statusPill.setTag("statusPill");
        statusPill.setTextSize(11);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(context, 6), 0, 0, 0);
        metaRow.addView(statusPill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textStack.addView(metaRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(textStack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private String buildProjectSummary(@NonNull GhostexDrawerItem item) {
        if (item.attentionCount > 0) return item.attentionCount + " attention · " + item.sessionCount + " sessions";
        if (item.workingCount > 0) return item.workingCount + " working · " + item.sessionCount + " sessions";
        if (item.sleepingCount == item.sessionCount) return item.sessionCount + " sleeping";
        return item.sessionCount == 1 ? "1 session" : item.sessionCount + " sessions";
    }

    private String statusLabel(@NonNull GhostexRemoteSession session) {
        if (session.isFocused) return "active";
        return session.displayStatus();
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
