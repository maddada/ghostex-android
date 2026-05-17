package com.termux.app.ghostex;

import android.widget.TextView;

import androidx.core.view.ViewCompat;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GhostexTextSemanticsTest {

    /*
    CDXC:AndroidSidebar 2026-05-17-17:47:
    Styled Ghostex dialogs own their title rows, so heading semantics must be
    applied by app code. Keep this helper tested so future visual polish does
    not quietly remove screen-reader structure from onboarding and settings.
    */
    @Test
    public void marksGhostexPanelTitleAsAccessibilityHeading() {
        TextView title = new TextView(RuntimeEnvironment.getApplication());

        GhostexTextSemantics.markHeading(title);

        Assert.assertTrue(ViewCompat.isAccessibilityHeading(title));
    }

}
