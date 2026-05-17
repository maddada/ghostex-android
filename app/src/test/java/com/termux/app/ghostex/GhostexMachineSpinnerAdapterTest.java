package com.termux.app.ghostex;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
public final class GhostexMachineSpinnerAdapterTest {

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-12:51:
    The machine dropdown is a primary recovery control. Test its custom row
    styling so it does not regress to low-contrast Android default spinner text
    on the Ghostex dark drawer.

    CDXC:AndroidConnectionManagement 2026-05-17-19:47:
    Multi-machine recovery should feel like the Ghostex drawer, not a stock
    Android picker. Collapsed and dropdown rows keep rounded Ghostex surfaces
    while retaining the 48dp touch target tested below.

    CDXC:AndroidSidebar 2026-05-17-20:08:
    The saved-machine switcher should use the same neutral macOS sidebar
    foreground as the drawer and session cards, not the older blue-slate copy.
    */

    @Test
    public void collapsedMachineRowUsesGhostexTextColor() {
        ArrayList<String> items = new ArrayList<>();
        items.add("Studio Mac · madda@mac.tailnet.ts.net");
        GhostexMachineSpinnerAdapter adapter =
            new GhostexMachineSpinnerAdapter(RuntimeEnvironment.getApplication(), items);

        View view = adapter.getView(0, null, new FrameLayout(RuntimeEnvironment.getApplication()));

        Assert.assertTrue(view instanceof TextView);
        Assert.assertEquals("Studio Mac · madda@mac.tailnet.ts.net", ((TextView) view).getText().toString());
        Assert.assertEquals(Color.rgb(250, 250, 250), ((TextView) view).getCurrentTextColor());
    }

    /*
    CDXC:AndroidConnectionManagement 2026-05-17-21:33:
    The machine spinner includes the Add SSH machine recovery action. Keep rows
    at Android's 48dp touch target minimum so account switching and recovery do
    not become cramped in the drawer dropdown.
    */
    @Test
    public void machineRowsKeepMinimumTouchTarget() {
        ArrayList<String> items = new ArrayList<>();
        items.add("Add SSH machine");
        GhostexMachineSpinnerAdapter adapter =
            new GhostexMachineSpinnerAdapter(RuntimeEnvironment.getApplication(), items);

        TextView view = (TextView) adapter.getView(0, null,
            new FrameLayout(RuntimeEnvironment.getApplication()));
        int minimumTouchTarget = Math.round(48 *
            view.getResources().getDisplayMetrics().density);

        Assert.assertTrue(view.getMinHeight() >= minimumTouchTarget);
    }

    @Test
    public void dropdownMachineRowUsesDarkBackground() {
        ArrayList<String> items = new ArrayList<>();
        items.add("Add SSH machine");
        GhostexMachineSpinnerAdapter adapter =
            new GhostexMachineSpinnerAdapter(RuntimeEnvironment.getApplication(), items);

        TextView view = (TextView) adapter.getDropDownView(0, null,
            new FrameLayout(RuntimeEnvironment.getApplication()));

        Assert.assertEquals("Add SSH machine", view.getText().toString());
        Assert.assertEquals(Color.rgb(250, 250, 250), view.getCurrentTextColor());
        Assert.assertTrue(view.getBackground() instanceof GradientDrawable);
    }

}
