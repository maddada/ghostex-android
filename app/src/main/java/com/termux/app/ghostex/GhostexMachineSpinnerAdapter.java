package com.termux.app.ghostex;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

public final class GhostexMachineSpinnerAdapter extends ArrayAdapter<String> {

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-12:51:
    The saved-machine switcher lives on the dark Ghostex drawer. Do not rely on
    Android's default spinner text colors, which can render low-contrast rows;
    style both collapsed and dropdown rows so machine/account switching remains
    readable in the release UI.

    CDXC:AndroidConnectionManagement 2026-05-17-19:47:
    The machine switcher is the multi-account recovery control after reconnect
    failures. Give collapsed and dropdown rows the same rounded Ghostex surface
    treatment as the session cards so saved-machine management feels deliberate
    instead of like a stock Android spinner bolted onto the drawer.
    */
    public GhostexMachineSpinnerAdapter(@NonNull Context context, @NonNull List<String> items) {
        super(context, 0, items);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        TextView row = convertView instanceof TextView ? (TextView) convertView : createRow(parent);
        styleRow(row, false);
        row.setText(getItem(position));
        return row;
    }

    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        TextView row = convertView instanceof TextView ? (TextView) convertView : createRow(parent);
        styleRow(row, true);
        row.setText(getItem(position));
        return row;
    }

    private TextView createRow(@NonNull ViewGroup parent) {
        Context context = parent.getContext();
        TextView row = new TextView(context);
        row.setSingleLine(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        /*
        CDXC:AndroidConnectionManagement 2026-05-17-21:33:
        The saved-machine spinner is the account recovery path after reconnect
        failures, so every collapsed/dropdown row keeps a standard 48dp touch
        target even when the row wraps machine details onto two lines.
        */
        row.setMinHeight(dp(context, 48));
        row.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        return row;
    }

    private void styleRow(@NonNull TextView row, boolean dropdown) {
        row.setTextColor(GhostexPalette.FOREGROUND);
        row.setTextSize(dropdown ? 14 : 13);
        row.setTypeface(Typeface.DEFAULT, dropdown ? Typeface.NORMAL : Typeface.BOLD);
        row.setBackground(rowBackground(row.getContext(), dropdown));
    }

    private GradientDrawable rowBackground(@NonNull Context context, boolean dropdown) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(dropdown ? GhostexPalette.CARD : GhostexPalette.INPUT_BACKGROUND);
        drawable.setCornerRadius(dp(context, 8));
        drawable.setStroke(dp(context, 1), dropdown ? GhostexPalette.BORDER : Color.TRANSPARENT);
        return drawable;
    }

    private int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

}
