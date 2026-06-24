package com.termux.app.ghostex;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;

import org.junit.Assert;
import org.junit.Test;

public final class GhostexExtraKeysDefaultTest {

    @Test
    public void defaultExtraKeysUseShiftInsteadOfCtrlU() {
        /*
        CDXC:AndroidSettings 2026-06-23-08:40:
        Android's default extra-keys row should match iOS by exposing Shift where the old Ctrl-U line-clear macro lived.
        */
        String defaultExtraKeys = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS;

        Assert.assertTrue(defaultExtraKeys.contains("'SHIFT'"));
        Assert.assertFalse(defaultExtraKeys.contains("CTRL u"));
        Assert.assertFalse(defaultExtraKeys.contains("^u"));
    }

    @Test
    public void savedLegacyCtrlUDefaultMigratesToShiftDefault() {
        /*
        CDXC:AndroidSettings 2026-06-23-08:40:
        Persisted old-default toolbar strings should migrate to the new Shift default without rewriting arbitrary custom layouts.
        */
        Assert.assertEquals(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS,
            TermuxSharedProperties.getExtraKeysInternalPropertyValueFromValue(
                TermuxPropertyConstants.LEGACY_GHOSTEX_CTRL_U_EXTRA_KEYS));
    }

}
